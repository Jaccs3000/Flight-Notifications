package com.flightnotifications.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightnotifications.app.data.repository.LocalBudgetRepository
import com.flightnotifications.app.domain.model.BudgetReservationResult
import com.flightnotifications.app.domain.model.RequestStage
import kotlinx.coroutines.runBlocking
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

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProbeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LocalBudgetRepository(database.requestReservationDao(), { now }, { elapsed })
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
        val reservation = repository.reserve(RequestStage.OUTBOUND, "clock-change")

        assertFalse(snapshot.clockTrusted)
        assertEquals(0, snapshot.remainingInWindow)
        assertEquals(BudgetReservationResult.CLOCK_UNTRUSTED, reservation)
        assertEquals(0, database.requestReservationDao().countSince(Instant.EPOCH.toEpochMilli()))
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
}
