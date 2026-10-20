package com.flightnotifications.app.data.repository

import android.os.SystemClock
import com.flightnotifications.app.data.local.RequestReservationDao
import com.flightnotifications.app.data.local.RequestReservationEntity
import com.flightnotifications.app.domain.model.BudgetReservationResult
import com.flightnotifications.app.domain.model.LocalBudgetSnapshot
import com.flightnotifications.app.domain.model.RequestStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val maximumRequests: suspend () -> Int = { MAX_REQUESTS },
) : FlightRequestBudget {
    private val mutableSnapshots = MutableStateFlow<LocalBudgetSnapshot?>(null)
    val snapshots: StateFlow<LocalBudgetSnapshot?> = mutableSnapshots.asStateFlow()

    override suspend fun snapshot(): LocalBudgetSnapshot {
        val instant = now()
        val clock = dao.checkAndRecordClock(instant.toEpochMilli(), elapsedRealtime())
        val cutoffMillis = clock.effectiveNowMillis - WINDOW.toMillis()
        val used = dao.countSince(cutoffMillis)
        val maximum = maximumRequests().coerceIn(1, MAX_REQUESTS)
        return LocalBudgetSnapshot(
            usedInWindow = used,
            remainingInWindow = if (clock.trusted) (maximum - used).coerceAtLeast(0) else 0,
            windowEndsAt = Instant.ofEpochMilli(clock.effectiveNowMillis),
            clockTrusted = clock.trusted,
            maximumRequests = maximum,
        ).also { mutableSnapshots.value = it }
    }

    override suspend fun reserve(stage: RequestStage, runId: String): BudgetReservationResult {
        val instant = now()
        val maximum = maximumRequests().coerceIn(1, MAX_REQUESTS)
        val result = dao.reserveIfAvailable(
            reservation = RequestReservationEntity(
                runId = runId,
                stage = stage.name,
                createdAtEpochMillis = instant.toEpochMilli(),
                budgetClockMillis = 0,
            ),
            maximum = maximum,
            wallNow = instant.toEpochMilli(),
            elapsedNow = elapsedRealtime(),
        )
        val mapped = when (result) {
            RequestReservationDao.RESERVED -> BudgetReservationResult.RESERVED
            RequestReservationDao.LIMIT_REACHED -> BudgetReservationResult.LIMIT_REACHED
            RequestReservationDao.CLOCK_UNTRUSTED -> BudgetReservationResult.CLOCK_UNTRUSTED
            else -> error("Unknown local reservation result")
        }
        if (mapped == BudgetReservationResult.RESERVED) {
            try {
                snapshot()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A UI snapshot failure must not undo or hide an already committed reservation.
            }
        }
        return mapped
    }

    companion object {
        const val MAX_REQUESTS = 200
        val WINDOW: Duration = Duration.ofMillis(RequestReservationDao.WINDOW_MILLIS)
    }
}
