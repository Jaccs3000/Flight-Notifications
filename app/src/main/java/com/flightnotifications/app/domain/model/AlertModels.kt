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
    val datePairIndex: Int = 0,
    val datePairCount: Int = 1,
    val datePairScanned: Boolean = false,
    val candidateExplorationLimited: Boolean = false,
    /** Date-pair indexes whose outbound request succeeded, even if some return tokens failed. */
    val datePairScannedIndices: List<Int> = if (datePairScanned) listOf(datePairIndex) else emptyList(),
    /** Date-pair indexes attempted during this run; failed outbound requests still advance rotation. */
    val datePairAttemptedIndices: List<Int> = if (datePairScanned) listOf(datePairIndex) else emptyList(),
    /** Date-pair indexes with candidate coverage gaps or failed return-token requests. */
    val datePairIncompleteIndices: List<Int> = if (datePairScanned && candidateExplorationLimited) listOf(datePairIndex) else emptyList(),
    val candidatesCompleted: Int = 0,
    val candidateFailures: Int = 0,
    val accountAlias: String? = null,
)

