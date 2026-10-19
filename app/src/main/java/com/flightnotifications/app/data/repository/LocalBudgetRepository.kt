package com.flightnotifications.app.data.repository

import android.os.SystemClock
import com.flightnotifications.app.data.local.RequestReservationDao
import com.flightnotifications.app.data.local.RequestReservationEntity
import com.flightnotifications.app.domain.model.BudgetReservationResult
import com.flightnotifications.app.domain.model.LocalBudgetSnapshot
import com.flightnotifications.app.domain.model.RequestStage
import java.time.Duration
import java.time.Instant

interface FlightRequestBudget {
    suspend fun snapshot(): LocalBudgetSnapshot
    suspend fun reserve(stage: RequestStage, runId: String): BudgetReservationResult
}

class LocalBudgetRepository(
    private val dao: RequestReservationDao,
    private val now: () -> Instant = Instant::now,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : FlightRequestBudget {
    override suspend fun snapshot(): LocalBudgetSnapshot {
        val instant = now()
        val clock = dao.checkAndRecordClock(instant.toEpochMilli(), elapsedRealtime())
        val cutoffMillis = clock.effectiveNowMillis - WINDOW.toMillis()
        val used = dao.countSince(cutoffMillis)
        return LocalBudgetSnapshot(
            usedInWindow = used,
            remainingInWindow = if (clock.trusted) (MAX_REQUESTS - used).coerceAtLeast(0) else 0,
            windowEndsAt = Instant.ofEpochMilli(clock.effectiveNowMillis),
            clockTrusted = clock.trusted,
        )
    }

    override suspend fun reserve(stage: RequestStage, runId: String): BudgetReservationResult {
        val instant = now()
        val result = dao.reserveIfAvailable(
            reservation = RequestReservationEntity(
                runId = runId,
                stage = stage.name,
                createdAtEpochMillis = instant.toEpochMilli(),
                budgetClockMillis = 0,
            ),
            maximum = MAX_REQUESTS,
            wallNow = instant.toEpochMilli(),
            elapsedNow = elapsedRealtime(),
        )
        return when (result) {
            RequestReservationDao.RESERVED -> BudgetReservationResult.RESERVED
            RequestReservationDao.LIMIT_REACHED -> BudgetReservationResult.LIMIT_REACHED
            RequestReservationDao.CLOCK_UNTRUSTED -> BudgetReservationResult.CLOCK_UNTRUSTED
            else -> error("Unknown local reservation result")
        }
    }

    companion object {
        const val MAX_REQUESTS = 200
        val WINDOW: Duration = Duration.ofMillis(RequestReservationDao.WINDOW_MILLIS)
    }
}
