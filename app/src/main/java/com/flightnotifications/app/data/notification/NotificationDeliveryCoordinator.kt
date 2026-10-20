package com.flightnotifications.app.data.notification

import com.flightnotifications.app.data.local.NotificationOutboxEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface NotificationOutboxStore {
    suspend fun wasDelivered(fingerprint: String): Boolean
    suspend fun enqueue(value: NotificationOutboxEntity): Boolean
    suspend fun pending(): List<NotificationOutboxEntity>
    suspend fun complete(fingerprint: String): Boolean
    suspend fun discard(fingerprint: String)
}

fun interface NotificationPublisher {
    suspend fun publish(value: NotificationOutboxEntity)
}

enum class NotificationDeliveryResult { SENT, ALREADY_SENT, PERMISSION_MISSING, FAILED, DISCARDED }

/** Serializes dispatch and retries the same event ID without suppressing later reviews of the same fare. */
class NotificationDeliveryCoordinator(
    private val store: NotificationOutboxStore,
    private val publisher: NotificationPublisher,
) {
    private val mutex = Mutex()

    suspend fun deliver(value: NotificationOutboxEntity): NotificationDeliveryResult = mutex.withLock {
        if (store.wasDelivered(value.fingerprint)) return@withLock NotificationDeliveryResult.ALREADY_SENT
        if (!store.enqueue(value)) return@withLock NotificationDeliveryResult.ALREADY_SENT
        publishAndCommit(value)
    }

    /** Called by the next worker run after a process interruption or a temporary permission failure. */
    suspend fun retryPending(isStillValid: suspend (NotificationOutboxEntity) -> Boolean): List<NotificationDeliveryResult> =
        mutex.withLock {
            store.pending().map { pending ->
                if (!isStillValid(pending)) {
                    store.discard(pending.fingerprint)
                    NotificationDeliveryResult.DISCARDED
                } else if (store.wasDelivered(pending.fingerprint)) {
                    store.discard(pending.fingerprint)
                    NotificationDeliveryResult.ALREADY_SENT
                } else {
                    publishAndCommit(pending)
                }
            }
        }

    private suspend fun publishAndCommit(value: NotificationOutboxEntity): NotificationDeliveryResult = try {
        publisher.publish(value)
        if (store.complete(value.fingerprint)) NotificationDeliveryResult.SENT
        else if (store.wasDelivered(value.fingerprint)) NotificationDeliveryResult.ALREADY_SENT
        else NotificationDeliveryResult.FAILED
    } catch (cancelled: CancellationException) {
        // Cancellation can represent process shutdown. Keep the durable outbox for the next worker.
        throw cancelled
    } catch (_: SecurityException) {
        // Permission may be revoked between the permission check and NotificationManager.notify().
        NotificationDeliveryResult.PERMISSION_MISSING
    } catch (_: Exception) {
        // Leave the outbox item in place so a later worker run can retry it.
        NotificationDeliveryResult.FAILED
    }
}
