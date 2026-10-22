package com.flightnotifications.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightnotifications.app.data.repository.FlightAlertRepository
import com.flightnotifications.app.domain.model.AlertSearchResult
import com.flightnotifications.app.domain.model.AlertFlightSegment
import com.flightnotifications.app.domain.model.VerifiedRoundTripOffer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlightAlertRepositoryTest {
    private lateinit var database: ProbeDatabase
    private lateinit var dao: FlightAlertDao
    private lateinit var repository: FlightAlertRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProbeDatabase::class.java).allowMainThreadQueries().build()
        dao = database.flightAlertDao()
        repository = FlightAlertRepository(dao)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun nameAndFrequencyEditsKeepQuoteWhileMaximumAndCriteriaEditsInvalidateTheRightVersion() = runBlocking {
        val original = alert()
        dao.putAlert(original)
        assertTrue(repository.saveOutcome(original.id, verifiedResult(), original))

        // The ViewModel trims this field before calling the repository; keep this test at the
        // repository boundary and pass the already-normalized value.
        val renamed = repository.save(original.copy(name = "Viaje familiar", updatedAt = 2))
        assertEquals("Viaje familiar", renamed.name)
        assertEquals(original.criteriaVersion, renamed.criteriaVersion)
        assertEquals(original.notificationPolicyVersion, renamed.notificationPolicyVersion)
        assertEquals("VERIFICADO", repository.result(original.id)?.status)

        val frequencyChanged = repository.save(renamed.copy(frequencyHours = 12, updatedAt = 3))
        assertEquals(renamed.criteriaVersion, frequencyChanged.criteriaVersion)
        assertEquals(renamed.notificationPolicyVersion, frequencyChanged.notificationPolicyVersion)
        assertEquals("VERIFICADO", repository.result(original.id)?.status)

        val maximumChanged = repository.save(frequencyChanged.copy(maxTotalCop = 450_000, updatedAt = 4))
        assertEquals(frequencyChanged.criteriaVersion, maximumChanged.criteriaVersion)
        assertEquals(frequencyChanged.notificationPolicyVersion + 1, maximumChanged.notificationPolicyVersion)
        assertEquals("POLICY_CHANGED", repository.result(original.id)?.status)
        assertEquals(350_000L, repository.result(original.id)?.lastVerifiedTotalCop)

        val criteriaChanged = repository.save(maximumChanged.copy(directOnly = false, updatedAt = 5))
        assertEquals(maximumChanged.criteriaVersion + 1, criteriaChanged.criteriaVersion)
        assertEquals(maximumChanged.notificationPolicyVersion, criteriaChanged.notificationPolicyVersion)
        assertEquals("CRITERIA_CHANGED", repository.result(original.id)?.status)
        assertEquals(350_000L, repository.result(original.id)?.lastVerifiedTotalCop)
    }

    @Test
    fun resultFromAnOlderCriteriaVersionIsDiscardedAfterAnEdit() = runBlocking {
        val original = alert()
        dao.putAlert(original)
        val current = repository.save(original.copy(returnDate = "2027-04-02"))

        assertFalse(repository.saveOutcome(original.id, verifiedResult(), original))
        assertEquals(null, repository.result(original.id))
        assertEquals(original.criteriaVersion + 1, current.criteriaVersion)
    }

    @Test
    fun changingEachRouteOrDateFieldInvalidatesThePriorCriteriaVersion() = runBlocking {
        val edits: List<(FlightAlertEntity) -> FlightAlertEntity> = listOf(
            { it.copy(originIata = "BOG") },
            { it.copy(destinationIata = "MDE") },
            { it.copy(outboundDate = "2027-04-01") },
            { it.copy(returnDate = "2027-04-02") },
            { it.copy(directOnly = false) },
        )

        edits.forEachIndexed { index, edit ->
            val base = alert().copy(id = "criteria-$index")
            dao.putAlert(base)
            dao.putResult(
                FlightAlertResultEntity(
                    alertId = base.id,
                    status = "VERIFICADO",
                    lastAttemptAt = 1,
                    networkReceivedAt = 2,
                    lastError = null,
                    lastVerifiedTotalCop = 350_000,
                    lastVerifiedAt = 2,
                    verifiedCriteria = "previous",
                    selectedOfferJson = "{}",
                    airlinesJson = "[]",
                    candidatesConsidered = 1,
                    candidateLimit = 3,
                    explorationLimited = false,
                    accountBalanceAtStart = 10,
                    flightGetsStarted = 2,
                ),
            )

            val saved = repository.save(edit(base))

            assertEquals("criterion edit $index", base.criteriaVersion + 1, saved.criteriaVersion)
            assertEquals("CRITERIA_CHANGED", repository.result(base.id)?.status)
            assertEquals(350_000L, repository.result(base.id)?.lastVerifiedTotalCop)
        }
    }

    @Test
    fun pauseAndResumeInvalidatePriorNotificationPolicyAndOnlyEnabledAlertsBecomeDue() = runBlocking {
        val original = alert().copy(nextDueAt = 1)
        dao.putAlert(original)

        repository.setEnabled(original.id, false)
        val paused = repository.find(original.id)!!
        assertFalse(paused.enabled)
        assertEquals(null, paused.nextDueAt)
        assertEquals(original.notificationPolicyVersion + 1, paused.notificationPolicyVersion)
        assertTrue(repository.dueAlerts(Long.MAX_VALUE).isEmpty())

        repository.setEnabled(original.id, true)
        val resumed = repository.find(original.id)!!
        assertTrue(resumed.enabled)
        assertEquals(paused.notificationPolicyVersion + 1, resumed.notificationPolicyVersion)
        assertTrue(repository.dueAlerts(Long.MAX_VALUE).any { it.id == original.id })
    }

    @Test
    fun repeatedPauseAndResumeAcrossSeveralAlertsAlwaysPersistsTheRequestedState() = runBlocking {
        val alerts = (0 until 4).map { index ->
            alert().copy(id = "toggle-stress-$index", enabled = true, nextDueAt = 1)
                .also { dao.putAlert(it) }
        }

        coroutineScope {
            alerts.map { original ->
                async(Dispatchers.IO) {
                    repeat(30) {
                        repository.setEnabled(original.id, false)
                        val paused = repository.find(original.id)!!
                        assertFalse("${original.id} should be paused", paused.enabled)
                        assertEquals("${original.id} pause should clear schedule", null, paused.nextDueAt)

                        repository.setEnabled(original.id, true)
                        val resumed = repository.find(original.id)!!
                        assertTrue("${original.id} should be active", resumed.enabled)
                        assertTrue("${original.id} should have a scheduled review", resumed.nextDueAt != null)
                    }
                }
            }.awaitAll()
        }

        alerts.forEach { original ->
            val final = repository.find(original.id)!!
            assertTrue("${original.id} should finish active", final.enabled)
            assertEquals("${original.id} should record every state change", original.notificationPolicyVersion + 60, final.notificationPolicyVersion)
            assertTrue("${original.id} should be due when resumed", repository.dueAlerts(Long.MAX_VALUE).any { it.id == original.id })
        }
    }

    private fun alert() = FlightAlertEntity(
        id = "repository-alert",
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
        nextDueAt = 1,
    )

    private fun verifiedResult() = AlertSearchResult(
        alertId = "repository-alert",
        status = "VERIFICADO",
        attemptAtMillis = 10,
        networkReceivedAtMillis = 11,
        error = null,
        offers = listOf(
            VerifiedRoundTripOffer(
                outbound = listOf(AlertFlightSegment("CTG", "CLO", "2027-03-25T08:00", "2027-03-25T09:30", "JetSMART", "JA100")),
                returning = listOf(AlertFlightSegment("CLO", "CTG", "2027-03-28T10:00", "2027-03-28T11:30", "JetSMART", "JA101")),
                outboundAirline = "JetSMART",
                returnAirline = "JetSMART",
                totalCop = 350_000,
                itineraryIdentity = "identity-1",
                fingerprint = "fingerprint-1",
            ),
        ),
        airlines = listOf("JetSMART"),
        candidatesConsidered = 1,
        candidateLimit = 3,
        explorationLimited = false,
        accountBalanceAtStart = 20,
        flightGetsStarted = 2,
    )
}
