package com.flightnotifications.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightnotifications.app.data.repository.LocalBudgetRepository
import com.flightnotifications.app.domain.model.BudgetReservationResult
import com.flightnotifications.app.domain.model.RequestStage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class LocalBudgetRepositoryTest {
    private lateinit var database: ProbeDatabase
    private lateinit var repository: LocalBudgetRepository
    private var now = Instant.parse("2026-10-19T12:00:00Z")
    private var elapsed = 1_000L
    private var maximum = 200

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProbeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LocalBudgetRepository(database.requestReservationDao(), { now }, { elapsed }, { maximum })
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun reservationPersistsAndEnforcesThe200RequestWindow() = runBlocking {
        repeat(199) { index -> assertEquals(BudgetReservationResult.RESERVED, repository.reserve(RequestStage.OUTBOUND, "run-$index")) }
        assertEquals(1, repository.snapshot().remainingInWindow)
        assertEquals(BudgetReservationResult.RESERVED, repository.reserve(RequestStage.RETURN, "run-final"))
        assertEquals(BudgetReservationResult.LIMIT_REACHED, repository.reserve(RequestStage.OUTBOUND, "run-over-limit"))
        assertEquals(200, repository.snapshot().usedInWindow)
    }

    @Test
    fun oldReservationsLeaveTheRollingThirtyDayWindow() = runBlocking {
        assertEquals(BudgetReservationResult.RESERVED, repository.reserve(RequestStage.OUTBOUND, "first-run"))
        val elapsedWindow = 30L * 24L * 60L * 60L + 1L
        now = now.plusSeconds(elapsedWindow)
        elapsed += elapsedWindow * 1_000

        assertEquals(0, repository.snapshot().usedInWindow)
        assertEquals(200, repository.snapshot().remainingInWindow)
    }

    @Test
    fun unexpectedWallClockChangeBlocksFlightRequestReservations() = runBlocking {
        repository.snapshot() // Establish the first trusted wall/elapsed clock pair.
        now = now.plusSeconds(60 * 60)

        val snapshot = repository.snapshot()

        assertFalse(snapshot.clockTrusted)
        assertEquals(0, snapshot.remainingInWindow)
        assertEquals(0, database.requestReservationDao().countSince(Instant.EPOCH.toEpochMilli()))

        now = now.plusSeconds(1)
        elapsed += 1_000
        assertTrue(repository.snapshot().clockTrusted)
        assertEquals(BudgetReservationResult.RESERVED, repository.reserve(RequestStage.OUTBOUND, "clock-change-recovered"))
    }

    @Test
    fun rebootWithClockMovedBackwardReanchorsAndRecoversWithoutExpiringReservations() = runBlocking {
        assertEquals(BudgetReservationResult.RESERVED, repository.reserve(RequestStage.OUTBOUND, "before-backward-clock-reboot"))
        now = now.minusSeconds(60L * 60L)
        elapsed = 100L

        val anomaly = repository.snapshot()
        assertFalse(anomaly.clockTrusted)
        assertEquals(1, anomaly.usedInWindow)

        now = now.plusSeconds(1)
        elapsed += 1_000
        val recovered = repository.snapshot()
        assertTrue(recovered.clockTrusted)
        assertEquals(1, recovered.usedInWindow)
        assertEquals(BudgetReservationResult.RESERVED, repository.reserve(RequestStage.OUTBOUND, "after-backward-clock-reboot"))
    }

    @Test
    fun rebootUptimeOvertakingOldUptimeRecoversAfterOneConservativeObservation() = runBlocking {
        assertEquals(BudgetReservationResult.RESERVED, repository.reserve(RequestStage.OUTBOUND, "before-long-uptime-reboot"))
        // The new uptime is greater than the prior observation, but the phone spent one day powered off.
        now = now.plusSeconds(61L * 24L * 60L * 60L)
        elapsed = 60L * 24L * 60L * 60L * 1_000

        val anomaly = repository.snapshot()
        assertFalse(anomaly.clockTrusted)
        assertEquals(1, anomaly.usedInWindow)

        now = now.plusSeconds(1)
        elapsed += 1_000
        val recovered = repository.snapshot()
        assertTrue(recovered.clockTrusted)
        assertEquals(1, recovered.usedInWindow)
    }

    @Test
    fun forwardWallClockChangeAcrossRebootDoesNotExpireReservationsEarly() = runBlocking {
        assertEquals(BudgetReservationResult.RESERVED, repository.reserve(RequestStage.OUTBOUND, "before-reboot"))
        now = now.plusSeconds(60L * 24L * 60L * 60L)
        elapsed = 100L // A lower elapsedRealtime value identifies a reboot.

        val snapshot = repository.snapshot()

        assertTrue(snapshot.clockTrusted)
        assertEquals(1, snapshot.usedInWindow)
        assertEquals(199, snapshot.remainingInWindow)
    }

    @Test
    fun loweringLocalMaximumPreservesReservationsAndBlocksAtTheNewLimit() = runBlocking {
        assertEquals(200, repository.snapshot().remainingInWindow)
        assertEquals(200, repository.snapshots.value?.remainingInWindow)
        repeat(5) { index -> assertEquals(BudgetReservationResult.RESERVED, repository.reserve(RequestStage.OUTBOUND, "before-reduce-$index")) }
        assertEquals(195, repository.snapshots.value?.remainingInWindow)
        maximum = 3

        val snapshot = repository.snapshot()

        assertEquals(5, snapshot.usedInWindow)
        assertEquals(3, snapshot.maximumRequests)
        assertEquals(0, snapshot.remainingInWindow)
        assertEquals(0, repository.snapshots.value?.remainingInWindow)
        assertEquals(BudgetReservationResult.LIMIT_REACHED, repository.reserve(RequestStage.RETURN, "after-reduce"))
        assertEquals(5, database.requestReservationDao().countSince(Instant.EPOCH.toEpochMilli()))
    }

    @Test
    fun concurrentReservationsNeverExceedTheConfiguredBudget() = runBlocking {
        maximum = 10
        repository.snapshot()

        val results = coroutineScope {
            (0 until 30).map { index ->
                async(Dispatchers.IO) {
                    repository.reserve(RequestStage.OUTBOUND, "concurrent-$index")
                }
            }.map { it.await() }
        }

        assertEquals(10, results.count { it == BudgetReservationResult.RESERVED })
        assertEquals(20, results.count { it == BudgetReservationResult.LIMIT_REACHED })
        assertEquals(10, repository.snapshot().usedInWindow)
    }
}
