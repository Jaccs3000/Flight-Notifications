package com.flightnotifications.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flight_request_reservations")
data class RequestReservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val stage: String,
    val createdAtEpochMillis: Long,
    val budgetClockMillis: Long,
)
