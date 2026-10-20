package com.flightnotifications.app.data.notification

import com.flightnotifications.app.data.local.NotificationOutboxEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeliveryCoordinatorTest {
    @Test
    fun processInterruptionAfterPublishLeavesOutboxAndNextRunCompletesWithoutDuplicateHistory() = runBlocking {
        val store = FakeStore().apply { failNextComplete = true }
        var published = 0
        val firstProcess = NotificationDeliveryCoordinator(store, NotificationPublisher { published++ })

        val interrupted = runCatching { firstProcess.deliver(pending()) }.exceptionOrNull()

        assertTrue(interrupted is SimulatedProcessDeath)
        assertEquals(1, published)
        assertEquals(1, store.pending().size)
        assertFalse(store.wasDelivered(pending().fingerprint))

        val afterRestart = NotificationDeliveryCoordinator(store, NotificationPublisher { published++ })
        val recovered = afterRestart.retryPending { true }

        assertEquals(listOf(NotificationDeliveryResult.SENT), recovered)
        assertEquals(2, published) // stable Android notification ID updates the existing visible notice
        assertTrue(store.wasDelivered(pending().fingerprint))
        assertTrue(store.pending().isEmpty())
        assertEquals(1, store.committedCount)
    }

    @Test
    fun permissionRevokedBetweenEnqueueAndPublishKeepsNoticeForRetry() = runBlocking {
        val store = FakeStore()
        var permissionGranted = false
        var published = 0
        val coordinator = NotificationDeliveryCoordinator(
            store,
            NotificationPublisher {
                published++
                if (!permissionGranted) throw SecurityException("permission revoked")
            },
        )

        assertEquals(NotificationDeliveryResult.PERMISSION_MISSING, coordinator.deliver(pending()))
        assertEquals(1, store.pending().size)
        assertFalse(store.wasDelivered(pending().fingerprint))

        permissionGranted = true
        assertEquals(listOf(NotificationDeliveryResult.SENT), coordinator.retryPending { true })
        assertEquals(2, published)
        assertTrue(store.wasDelivered(pending().fingerprint))
    }

    @Test
    fun concurrentAttemptsForSameFingerprintPublishAndCommitOnlyOnce() = runBlocking {
        val store = FakeStore()
        val publishStarted = CompletableDeferred<Unit>()
        val finishPublish = CompletableDeferred<Unit>()
        var published = 0
        val coordinator = NotificationDeliveryCoordinator(
            store,
            NotificationPublisher {
                published++
                publishStarted.complete(Unit)
                finishPublish.await()
            },
        )

        val first = async { coordinator.deliver(pending()) }
        publishStarted.await()
        val second = async { coordinator.deliver(pending()) }
        finishPublish.complete(Unit)

        assertEquals(NotificationDeliveryResult.SENT, first.await())
        assertEquals(NotificationDeliveryResult.ALREADY_SENT, second.await())
        assertEquals(1, published)
        assertEquals(1, store.committedCount)
    }

    @Test
    fun sameFareCanBeDeliveredAgainForANewReviewButRetryOfOneReviewIsIdempotent() = runBlocking {
        val store = FakeStore()
        val published = mutableListOf<NotificationOutboxEntity>()
        val coordinator = NotificationDeliveryCoordinator(store, NotificationPublisher { published += it })
        val firstReview = pending().copy(fingerprint = "review-1")
        val secondReview = pending().copy(fingerprint = "review-2")

        assertEquals(NotificationDeliveryResult.SENT, coordinator.deliver(firstReview))
        assertEquals(NotificationDeliveryResult.ALREADY_SENT, coordinator.deliver(firstReview))
        assertEquals(NotificationDeliveryResult.SENT, coordinator.deliver(secondReview))

        assertEquals(listOf(350_000L, 350_000L), published.map { it.priceCop })
        assertEquals(listOf("review-1", "review-2"), published.map { it.fingerprint })
        assertEquals(2, store.committedCount)
    }

    @Test
    fun stalePendingNoticeIsDiscardedWithoutPublishing() = runBlocking {
        val store = FakeStore()
        store.enqueue(pending())
        var published = 0
        val coordinator = NotificationDeliveryCoordinator(store, NotificationPublisher { published++ })

        assertEquals(listOf(NotificationDeliveryResult.DISCARDED), coordinator.retryPending { false })
        assertEquals(0, published)
        assertTrue(store.pending().isEmpty())
    }

    private fun pending() = NotificationOutboxEntity(
        fingerprint = "fingerprint-verified",
        alertId = "alert-1",
        itineraryIdentity = "itinerary-1",
        priceCop = 350_000,
        createdAt = 1_000,
        routeSummary = "CTG ↔ CLO · 2027-03-25 / 2027-03-28",
        outboundCarrier = "JetSMART",
        returnCarrier = "JetSMART",
        explorationLimited = false,
        criteriaVersion = 1,
        notificationPolicyVersion = 1,
    )

    private class FakeStore : NotificationOutboxStore {
        private val queued = linkedMapOf<String, NotificationOutboxEntity>()
        private val delivered = mutableSetOf<String>()
        var committedCount = 0
            private set
        var failNextComplete = false

        override suspend fun wasDelivered(fingerprint: String) = fingerprint in delivered

        override suspend fun enqueue(value: NotificationOutboxEntity): Boolean {
            if (value.fingerprint in delivered) return false
            queued.putIfAbsent(value.fingerprint, value)
            return true
        }

        override suspend fun pending() = queued.values.toList()

        override suspend fun complete(fingerprint: String): Boolean {
            if (failNextComplete) {
                failNextComplete = false
                throw SimulatedProcessDeath()
            }
            val item = queued.remove(fingerprint) ?: return false
            if (!delivered.add(fingerprint)) return false
            assertTrue(item.priceCop > 0)
            committedCount++
            return true
        }

        override suspend fun discard(fingerprint: String) {
            queued.remove(fingerprint)
        }
    }

    private class SimulatedProcessDeath : CancellationException("simulated process stop")
}
