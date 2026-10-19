package com.flightnotifications.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget_clock_observation")
data class BudgetClockObservationEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val wallClockEpochMillis: Long,
    val elapsedRealtimeMillis: Long,
    val effectiveBudgetClockMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

data class BudgetClockCheck(
    val trusted: Boolean,
    val effectiveNowMillis: Long,
)
