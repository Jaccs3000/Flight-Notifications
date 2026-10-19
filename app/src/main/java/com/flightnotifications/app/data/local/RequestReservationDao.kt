package com.flightnotifications.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class RequestReservationDao {
    @Query("DELETE FROM flight_request_reservations WHERE budgetClockMillis < :cutoff")
    protected abstract suspend fun deleteBefore(cutoff: Long)

    @Query("SELECT COUNT(*) FROM flight_request_reservations WHERE budgetClockMillis >= :cutoff")
    abstract suspend fun countSince(cutoff: Long): Int

    @Insert
    protected abstract suspend fun insert(reservation: RequestReservationEntity)

    @Query("SELECT * FROM budget_clock_observation WHERE id = 1 LIMIT 1")
    protected abstract suspend fun getClockObservation(): BudgetClockObservationEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    protected abstract suspend fun saveClockObservation(observation: BudgetClockObservationEntity)

    protected suspend fun checkClock(wallNow: Long, elapsedNow: Long): BudgetClockCheck {
        val previous = getClockObservation()
        if (previous == null) {
            saveClockObservation(
                BudgetClockObservationEntity(
                    wallClockEpochMillis = wallNow,
                    elapsedRealtimeMillis = elapsedNow,
                    effectiveBudgetClockMillis = wallNow,
                ),
            )
            return BudgetClockCheck(trusted = true, effectiveNowMillis = wallNow)
        }

        val wallDelta = wallNow - previous.wallClockEpochMillis
        val elapsedDelta = elapsedNow - previous.elapsedRealtimeMillis
        val rebootDetected = elapsedDelta < 0
        if (rebootDetected && wallDelta < 0) {
            return BudgetClockCheck(trusted = false, effectiveNowMillis = previous.effectiveBudgetClockMillis)
        }
        if (!rebootDetected &&
            (wallDelta < 0 || kotlin.math.abs(wallDelta - elapsedDelta) > CLOCK_DRIFT_TOLERANCE_MILLIS)
        ) {
            return BudgetClockCheck(trusted = false, effectiveNowMillis = previous.effectiveBudgetClockMillis)
        }

        // On reboot, wall time may have been moved forward while the app was closed. Freeze
        // the budget clock through downtime and advance it only by uptime so old reservations
        // cannot expire early because of a manual clock change.
        val safeElapsedAdvance = if (rebootDetected) elapsedNow.coerceAtLeast(0) else elapsedDelta
        val effectiveNow = (previous.effectiveBudgetClockMillis + safeElapsedAdvance)
            .coerceAtLeast(previous.effectiveBudgetClockMillis)
        saveClockObservation(
            BudgetClockObservationEntity(
                wallClockEpochMillis = wallNow,
                elapsedRealtimeMillis = elapsedNow,
                effectiveBudgetClockMillis = effectiveNow,
            ),
        )
        return BudgetClockCheck(trusted = true, effectiveNowMillis = effectiveNow)
    }

    @Transaction
    open suspend fun checkAndRecordClock(wallNow: Long, elapsedNow: Long): BudgetClockCheck =
        checkClock(wallNow, elapsedNow)

    @Transaction
    open suspend fun reserveIfAvailable(
        reservation: RequestReservationEntity,
        maximum: Int,
        wallNow: Long,
        elapsedNow: Long,
    ): String {
        val clock = checkClock(wallNow, elapsedNow)
        if (!clock.trusted) return CLOCK_UNTRUSTED
        val cutoff = clock.effectiveNowMillis - WINDOW_MILLIS
        deleteBefore(cutoff)
        if (countSince(cutoff) >= maximum) return LIMIT_REACHED
        insert(reservation.copy(budgetClockMillis = clock.effectiveNowMillis))
        return RESERVED
    }

    companion object {
        const val CLOCK_UNTRUSTED = "CLOCK_UNTRUSTED"
        const val LIMIT_REACHED = "LIMIT_REACHED"
        const val RESERVED = "RESERVED"
        const val CLOCK_DRIFT_TOLERANCE_MILLIS = 15 * 60 * 1000L
        const val WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
