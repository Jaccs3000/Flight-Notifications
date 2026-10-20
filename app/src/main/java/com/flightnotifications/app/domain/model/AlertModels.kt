package com.flightnotifications.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AlertFlightSegment(
    val fromIata: String,
    val toIata: String,
    val departureLocal: String?,
    val arrivalLocal: String?,
    val airline: String,
    val flightNumber: String,
)

@Serializable
data class VerifiedRoundTripOffer(
    val outbound: List<AlertFlightSegment>,
    val returning: List<AlertFlightSegment>,
    val outboundAirline: String,
    val returnAirline: String,
    val totalCop: Long,
    val itineraryIdentity: String,
    val fingerprint: String,
)

data class AlertSearchResult(
    val alertId: String,
    val status: String,
    val attemptAtMillis: Long,
    val networkReceivedAtMillis: Long?,
    val error: String?,
    val offers: List<VerifiedRoundTripOffer>,
    val airlines: List<String>,
    val candidatesConsidered: Int,
    val candidateLimit: Int,
    val explorationLimited: Boolean,
    val accountBalanceAtStart: Int?,
    val flightGetsStarted: Int,
    val googleFlightsUrl: String? = null,
)

