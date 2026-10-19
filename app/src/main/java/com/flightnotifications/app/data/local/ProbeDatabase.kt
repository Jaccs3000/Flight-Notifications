package com.flightnotifications.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RequestReservationEntity::class, BudgetClockObservationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ProbeDatabase : RoomDatabase() {
    abstract fun requestReservationDao(): RequestReservationDao
}
