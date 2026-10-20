package com.flightnotifications.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class SearchCriteria(
    val outboundDate: LocalDate,
    val returnDate: LocalDate,
    val directOnly: Boolean,
    val origin: String = "CTG",
    val destination: String = "CLO",
) {
    fun validationError(today: LocalDate = LocalDate.now(COLOMBIA_ZONE)): String? = when {
        !IATA.matches(origin) || !IATA.matches(destination) -> "Los aeropuertos deben usar códigos IATA de tres letras."
        origin == destination -> "El origen y el destino deben ser distintos."
        outboundDate.isBefore(today) -> "La fecha de ida no puede estar en el pasado."
        !returnDate.isAfter(outboundDate) -> "La fecha de regreso debe ser posterior a la ida."
        else -> null
    }

    companion object {
        val COLOMBIA_ZONE: ZoneId = ZoneId.of("America/Bogota")
        private val IATA = Regex("[A-Z]{3}")
    }
}

data class AccountSnapshot(
    val accountStatus: String,
    val planSearchesLeft: Int?,
    val totalSearchesLeft: Int?,
    val searchesPerMonth: Int?,
    val thisMonthUsage: Int?,
    val extraCredits: Int?,
    val hourlyLimit: Int?,
    val thisHourSearches: Int?,
    val planRenewalDate: LocalDate?,
    val checkedAt: Instant,
) {
    val hourlySearchesLeft: Int?
        get() = if (hourlyLimit != null && thisHourSearches != null && hourlyLimit >= thisHourSearches) {
            hourlyLimit - thisHourSearches
        } else null

    val conservativeSearchesLeft: Int?
        get() = listOfNotNull(totalSearchesLeft, hourlySearchesLeft)
            .takeIf { it.size == 2 && it.all { value -> value >= 0 } }
            ?.minOrNull()

    val canStartFlightProbe: Boolean
        get() = accountStatus.equals("Active", ignoreCase = true) && (conservativeSearchesLeft ?: 0) >= 2
}

data class AirportPoint(
    val code: String,
    val name: String?,
    val localDateTime: LocalDateTime?,
)

data class FlightSegment(
    val departure: AirportPoint,
    val arrival: AirportPoint,
    val airline: String?,
    val flightNumber: String?,
)

data class FlightOption(
    val segments: List<FlightSegment>,
    val priceCop: Long?,
    val type: String?,
    val departureToken: String?,
    val sourceGroup: String,
) {
    val carriers: List<String>
        get() = segments.mapNotNull { it.airline?.trim()?.takeIf(String::isNotEmpty) }.distinct()
}

data class ParsedFlights(
    val options: List<FlightOption>,
    val airlinesDetected: Set<String>,
    val responseStatus: String,
    val googleFlightsUrl: String?,
    val malformedOptions: Int,
)

data class ProbeOutcome(
    val criteria: SearchCriteria,
    val accountAtStart: AccountSnapshot,
    val outbound: FlightOption?,
    val returnOptions: List<FlightOption>,
    val selectedReturn: FlightOption?,
    val airlinesDetected: Set<String>,
    val googleFlightsUrl: String?,
    val startedAt: Instant,
    val completedAt: Instant,
    val flightGetsStarted: Int,
    val statusLabel: String,
    val detail: String,
    val malformedOptions: Int,
    val verifiedRoundTripPriceCop: Long? = null,
) {
    val wingoDetected: Boolean
        get() = airlinesDetected.any { it.contains("Wingo", ignoreCase = true) }
}

data class LocalBudgetSnapshot(
    val usedInWindow: Int,
    val remainingInWindow: Int,
    val windowEndsAt: Instant,
    val clockTrusted: Boolean = true,
    val maximumRequests: Int = 200,
)

enum class RequestStage { OUTBOUND, RETURN }

enum class BudgetReservationResult { RESERVED, LIMIT_REACHED, CLOCK_UNTRUSTED }
