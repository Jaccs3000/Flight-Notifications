package com.flightnotifications.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import com.flightnotifications.app.data.repository.FlightAlertRepository
import com.flightnotifications.app.domain.model.AlertSearchResult
import com.flightnotifications.app.domain.model.AlertFlightSegment
import com.flightnotifications.app.domain.model.VerifiedRoundTripOffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlightAlertDaoTest {
    private lateinit var database: ProbeDatabase
    private lateinit var dao: FlightAlertDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProbeDatabase::class.java).allowMainThreadQueries().build()
        dao = database.flightAlertDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun clearingVisibleNotificationsPreservesDeduplicationAndLowestSentPrice() = runBlocking {
        val alert = alert()
        dao.putAlert(alert)
        val first = notice(alert, "fingerprint-350", 350_000, "itinerary-1", 1_000)
        dao.commitNotification(first, fingerprint(alert, first))
        dao.commitNotification(first, fingerprint(alert, first))

        assertEquals(1, dao.observeNotifications().first().size)
        assertNotNull(dao.getFingerprint("fingerprint-350"))
        assertEquals(350_000L, dao.getLowestPrice("itinerary-1")?.lowestPriceCop)

        dao.clearVisibleNotifications()

        assertTrue(dao.observeNotifications().first().isEmpty())
        assertNotNull(dao.getFingerprint("fingerprint-350"))
        assertEquals(350_000L, dao.getLowestPrice("itinerary-1")?.lowestPriceCop)

        val higher = notice(alert, "fingerprint-360", 360_000, "itinerary-1", 2_000)
        dao.commitNotification(higher, fingerprint(alert, higher))
        assertEquals(350_000L, dao.getLowestPrice("itinerary-1")?.lowestPriceCop)
        val lower = notice(alert, "fingerprint-340", 340_000, "itinerary-1", 3_000)
        dao.commitNotification(lower, fingerprint(alert, lower))
        assertEquals(340_000L, dao.getLowestPrice("itinerary-1")?.lowestPriceCop)

        dao.deleteAlert(alert.id)
        assertNull(dao.getFingerprint("fingerprint-350"))
        assertNull(dao.getLowestPrice("itinerary-1"))
        assertTrue(dao.observeNotifications().first().isEmpty())
    }

    @Test
    fun newInstallSeedsExampleOnlyOnceEvenAfterTheUserDeletesIt() = runBlocking {
        val example = alert().copy(id = "sample", enabled = false)
        dao.initializeExampleIfNewInstall(example)
        assertEquals(example.id, dao.observeAlerts().first().single().id)

        dao.deleteAlert(example.id)
        dao.initializeExampleIfNewInstall(example)

        assertTrue(dao.observeAlerts().first().isEmpty())
    }

    @Test
    fun failedNewCriteriaAttemptPreservesLastVerifiedFareAndItsOriginalCriteria() = runBlocking {
        val alert = alert()
        dao.putAlert(alert)
        val oldCriteria = "CTG-CLO|2027-03-25|2027-03-28|direct=true|adults=1|minors=[]"
        dao.putResult(
            FlightAlertResultEntity(
                alertId = alert.id,
                status = "VERIFICADO",
                lastAttemptAt = 1_000,
                attemptCriteria = oldCriteria,
                attemptMaximumCop = 500_000,
                networkReceivedAt = 1_010,
                lastError = null,
                lastVerifiedTotalCop = 350_000,
                lastVerifiedAt = 1_010,
                verifiedCriteria = oldCriteria,
                selectedOfferJson = "{\"offer\":true}",
                airlinesJson = "[\"JetSMART\"]",
                candidatesConsidered = 1,
                candidateLimit = 3,
                explorationLimited = false,
                accountBalanceAtStart = 10,
                flightGetsStarted = 2,
            ),
        )

        val newAlert = alert.copy(
            originIata = "BOG",
            outboundDate = "2027-04-01",
            returnDate = "2027-04-05",
            maxTotalCop = 400_000,
            criteriaVersion = 2,
        )
        dao.putAlert(newAlert)
        val repository = FlightAlertRepository(dao)
        val saved = repository.saveOutcome(
            alert.id,
            AlertSearchResult(
                alertId = alert.id,
                status = "ERROR",
                attemptAtMillis = 2_000,
                networkReceivedAtMillis = null,
                error = "NETWORK",
                offers = emptyList(),
                airlines = emptyList(),
                candidatesConsidered = 0,
                candidateLimit = 3,
                explorationLimited = false,
                accountBalanceAtStart = null,
                flightGetsStarted = 0,
            ),
            newAlert,
        )

        assertTrue(saved)
        val result = dao.getResult(alert.id)!!
        assertEquals("ERROR", result.status)
        assertEquals("BOG-CLO|2027-04-01|2027-04-05|direct=true|adults=1|minors=[]", result.attemptCriteria)
        assertEquals(400_000L, result.attemptMaximumCop)
        assertEquals(350_000L, result.lastVerifiedTotalCop)
        assertEquals(oldCriteria, result.verifiedCriteria)
        assertEquals("{\"offer\":true}", result.selectedOfferJson)
    }

    @Test
    fun expiryCleanupRemovesDeduplicationButKeepsVisibleNotificationRecord() = runBlocking {
        val alert = alert()
        dao.putAlert(alert)
        val notice = notice(alert, "fingerprint-expired", 350_000, "itinerary-expired", 1_000)
        dao.commitNotification(notice, fingerprint(alert, notice))

        dao.clearExpiredNotificationState(alert.id)

        assertNull(dao.getFingerprint(notice.fingerprint))
        assertNull(dao.getLowestPrice("itinerary-expired"))
        assertEquals(1, dao.observeNotifications().first().size)
    }

    @Test
    fun latestVerifiedReviewPersistsEveryVerifiedOptionForDetails() = runBlocking {
        val alert = alert()
        dao.putAlert(alert)
        val offers = listOf(verifiedOffer("offer-1", 350_000), verifiedOffer("offer-2", 390_000))
        val repository = FlightAlertRepository(dao)

        assertTrue(repository.saveOutcome(
            alert.id,
            AlertSearchResult(
                alertId = alert.id,
                status = "VERIFICADO",
                attemptAtMillis = 1_000,
                networkReceivedAtMillis = 1_010,
                error = null,
                offers = offers,
                airlines = listOf("JetSMART", "Avianca"),
                candidatesConsidered = 2,
                candidateLimit = 3,
                explorationLimited = true,
                accountBalanceAtStart = 10,
                flightGetsStarted = 3,
            ),
            alert,
        ))

        val result = dao.getResult(alert.id)!!
        val stored = kotlinx.serialization.json.Json.decodeFromString<List<VerifiedRoundTripOffer>>(result.verifiedOffersJson!!)
        assertEquals(2, stored.size)
        assertEquals(350_000L, result.lastVerifiedTotalCop)
        assertEquals("offer-1", stored.first().fingerprint)
    }

    @Test
    fun concurrentDuplicateCommitsCreateOneVisibleNoticeAndOneFingerprint() = runBlocking {
        val alert = alert()
        dao.putAlert(alert)
        val notification = notice(alert, "fingerprint-concurrent", 350_000, "itinerary-concurrent", 1_000)
        val sentFingerprint = fingerprint(alert, notification)

        coroutineScope {
            (1..16).map {
                async(Dispatchers.IO) { dao.commitNotification(notification, sentFingerprint) }
            }.forEach { it.await() }
        }

        assertEquals(1, dao.observeNotifications().first().size)
        assertNotNull(dao.getFingerprint(notification.fingerprint))
        assertEquals(350_000L, dao.getLowestPrice(notification.itineraryIdentity)?.lowestPriceCop)
    }

    @Test
    fun visibleNotificationHistoryIsCappedAtOneHundredWithoutLosingDeduplication() = runBlocking {
        val alert = alert()
        dao.putAlert(alert)

        (1..105).forEach { index ->
            val notification = notice(alert, "fingerprint-$index", 350_000L - index, "itinerary-$index", index.toLong())
            dao.commitNotification(notification, fingerprint(alert, notification))
        }

        val visible = dao.observeNotifications().first()
        assertEquals(100, visible.size)
        assertEquals("fingerprint-105", visible.first().fingerprint)
        assertEquals("fingerprint-6", visible.last().fingerprint)
        assertNotNull(dao.getFingerprint("fingerprint-1"))
        assertEquals(350_000L - 1, dao.getLowestPrice("itinerary-1")?.lowestPriceCop)
    }

    @Test
    fun pendingNotificationCommitsHistoryAndFingerprintAtomicallyAndCannotBeQueuedAgain() = runBlocking {
        val alert = alert()
        dao.putAlert(alert)
        val pending = NotificationOutboxEntity(
            fingerprint = "fingerprint-outbox",
            alertId = alert.id,
            itineraryIdentity = "itinerary-outbox",
            priceCop = 345_000,
            createdAt = 5_000,
            routeSummary = "CTG ↔ CLO",
            outboundCarrier = "JetSMART",
            returnCarrier = "JetSMART",
            outboundDepartureLocal = "2027-03-25T06:30",
            returnDepartureLocal = "2027-03-28T18:45",
            explorationLimited = true,
            criteriaVersion = alert.criteriaVersion,
            notificationPolicyVersion = alert.notificationPolicyVersion,
        )

        assertTrue(dao.enqueueNotification(pending))
        assertEquals(1, dao.getPendingNotifications().size)
        assertTrue(dao.completePendingNotification(pending.fingerprint))

        assertTrue(dao.getPendingNotifications().isEmpty())
        assertNotNull(dao.getFingerprint(pending.fingerprint))
        assertEquals(345_000L, dao.getLowestPrice(pending.itineraryIdentity)?.lowestPriceCop)
        assertEquals(1, dao.observeNotifications().first().size)
        val savedNotice = dao.observeNotifications().first().single()
        assertEquals("2027-03-25T06:30", savedNotice.outboundDepartureLocal)
        assertEquals("2027-03-28T18:45", savedNotice.returnDepartureLocal)
        assertFalse(dao.enqueueNotification(pending))
        assertFalse(dao.completePendingNotification(pending.fingerprint))
    }

    private fun alert() = FlightAlertEntity(
        id = "alert-1",
        name = "CTG-CLO",
        originIata = "CTG",
        destinationIata = "CLO",
        outboundDate = "2027-03-25",
        returnDate = "2027-03-28",
        maxTotalCop = 500_000,
        frequencyHours = 24,
        directOnly = true,
        enabled = true,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun notice(alert: FlightAlertEntity, fingerprint: String, price: Long, itinerary: String, sentAt: Long) =
        SentNotificationEntity(
            id = fingerprint,
            alertId = alert.id,
            fingerprint = fingerprint,
            itineraryIdentity = itinerary,
            priceCop = price,
            sentAt = sentAt,
            routeSummary = "CTG ↔ CLO",
            outboundCarrier = "JetSMART",
            returnCarrier = "JetSMART",
        )

    private fun fingerprint(alert: FlightAlertEntity, notice: SentNotificationEntity) =
        NotifiedFingerprintEntity(
            fingerprint = notice.fingerprint,
            alertId = alert.id,
            itineraryIdentity = notice.itineraryIdentity,
            priceCop = notice.priceCop,
            firstSentAt = notice.sentAt,
        )

    private fun verifiedOffer(fingerprint: String, price: Long) = VerifiedRoundTripOffer(
        outbound = listOf(AlertFlightSegment("CTG", "CLO", "2027-03-25T07:00", "2027-03-25T08:30", "JetSMART", "JA100")),
        returning = listOf(AlertFlightSegment("CLO", "CTG", "2027-03-28T09:00", "2027-03-28T10:30", "JetSMART", "JA101")),
        outboundAirline = "JetSMART",
        returnAirline = "JetSMART",
        totalCop = price,
        itineraryIdentity = "identity-$fingerprint",
        fingerprint = fingerprint,
    )
}
