package com.flightnotifications.app.data.repository

import com.flightnotifications.app.data.local.FlightAlertDao
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.local.FlightAlertResultEntity
import com.flightnotifications.app.data.local.SentNotificationEntity
import com.flightnotifications.app.data.local.NotificationOutboxEntity
import com.flightnotifications.app.data.notification.NotificationOutboxStore
import com.flightnotifications.app.domain.model.AlertSearchResult
import com.flightnotifications.app.domain.model.MinorPassenger
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AlertWithResult(
    val alert: FlightAlertEntity,
    val result: FlightAlertResultEntity?,
)

class FlightAlertRepository(private val dao: FlightAlertDao) : NotificationOutboxStore {
    val alerts = combine(dao.observeAlerts(), dao.observeResults()) { items, results ->
        val byId = results.associateBy { it.alertId }
        items.map { AlertWithResult(it, byId[it.id]) }
    }
    val notifications = dao.observeNotifications()

    suspend fun currentAlerts(): List<AlertWithResult> {
        val byId = dao.getResults().associateBy { it.alertId }
        return dao.getAlerts().map { AlertWithResult(it, byId[it.id]) }
    }

    suspend fun initializeExample() {
        val now = System.currentTimeMillis()
        dao.initializeExampleIfNewInstall(
            FlightAlertEntity(
                id = "sample-ctg-clo-2027-03",
                name = "Cartagena ↔ Cali — marzo 2027",
                originIata = "CTG",
                destinationIata = "CLO",
                outboundDate = "2027-03-25",
                returnDate = "2027-03-28",
                maxTotalCop = 400_000,
                frequencyHours = 24,
                directOnly = true,
                enabled = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun save(alert: FlightAlertEntity): FlightAlertEntity {
        val previous = dao.getAlert(alert.id)
        val routeChanged = previous != null && (
            previous.originIata != alert.originIata || previous.destinationIata != alert.destinationIata ||
                previous.outboundDate != alert.outboundDate || previous.returnDate != alert.returnDate ||
                previous.flexibleDateSearch != alert.flexibleDateSearch || previous.outboundDateEnd != alert.outboundDateEnd ||
                previous.returnDateEnd != alert.returnDateEnd ||
                previous.directOnly != alert.directOnly || previous.adults18Plus != alert.adults18Plus ||
                previous.minorPassengersJson != alert.minorPassengersJson
            )
        val maximumChanged = previous != null && previous.maxTotalCop != alert.maxTotalCop
        val scheduleChanged = previous != null && previous.nextDueAt != alert.nextDueAt
        val nextDueAt = when {
            alert.enabled && scheduleChanged -> alert.nextDueAt
            alert.enabled && (routeChanged || maximumChanged) -> System.currentTimeMillis()
            else -> alert.nextDueAt
        }
        val saved = alert.copy(
            criteriaVersion = (previous?.criteriaVersion ?: alert.criteriaVersion) + if (routeChanged) 1 else 0,
            notificationPolicyVersion = (previous?.notificationPolicyVersion ?: alert.notificationPolicyVersion) +
                if (maximumChanged) 1 else 0,
            candidateRotation = previous?.candidateRotation ?: alert.candidateRotation,
            datePairRotation = if (routeChanged) 0 else (previous?.datePairRotation ?: alert.datePairRotation),
            nextDueAt = nextDueAt,
        )
        dao.putAlert(saved)
        if (routeChanged || maximumChanged) {
            dao.markResultAsPrevious(saved.id, if (routeChanged) "CRITERIA_CHANGED" else "POLICY_CHANGED")
        }
        return saved
    }
    suspend fun remove(id: String) = dao.deleteAlert(id)
    suspend fun find(id: String) = dao.getAlert(id)
    suspend fun enabledAlerts() = dao.getEnabledAlerts()
    suspend fun dueAlerts(now: Long) = dao.getDueAlerts(now)
    suspend fun deferExpired(id: String) = dao.deferEnabledAlert(id, Long.MAX_VALUE)
    suspend fun result(id: String) = dao.getResult(id)
    suspend fun fingerprint(id: String) = dao.getFingerprint(id)
    suspend fun lowestPrice(identity: String) = dao.getLowestPrice(identity)
    suspend fun clearVisibleNotifications() = dao.clearVisibleNotifications()
    suspend fun clearExpiredNotificationState(alertId: String) = dao.clearExpiredNotificationState(alertId)
    suspend fun recordNotification(notification: SentNotificationEntity, fingerprint: com.flightnotifications.app.data.local.NotifiedFingerprintEntity) =
        dao.commitNotification(notification, fingerprint)

    override suspend fun wasDelivered(fingerprint: String) = dao.getFingerprint(fingerprint) != null
    override suspend fun enqueue(value: NotificationOutboxEntity) = dao.enqueueNotification(value)
    override suspend fun pending() = dao.getPendingNotifications()
    override suspend fun complete(fingerprint: String) = dao.completePendingNotification(fingerprint)
    override suspend fun discard(fingerprint: String) = dao.discardPendingNotification(fingerprint)

    suspend fun saveOutcome(alertId: String, outcome: AlertSearchResult, alert: FlightAlertEntity): Boolean {
        val currentAlert = dao.getAlert(alertId) ?: return false
        if (currentAlert.criteriaVersion != alert.criteriaVersion) return false
        val previous = dao.getResult(alertId)
        val verified = outcome.offers.minByOrNull { it.totalCop }
        val criteria = alertCriteriaKey(alert)
        val sameAttemptCriteria = previous?.attemptCriteria == criteria
        val sameVerifiedCriteria = previous?.verifiedCriteria == criteria
        val requestedPairCount = outcome.datePairCount.coerceIn(1, 49)
        val scannedIndices = (outcome.datePairScannedIndices.ifEmpty {
            if (outcome.datePairScanned) listOf(outcome.datePairIndex) else emptyList()
        }).filter { it in 0 until requestedPairCount }.distinct()
        val incompleteIndices = (outcome.datePairIncompleteIndices.ifEmpty {
            if (outcome.datePairScanned && outcome.candidateExplorationLimited) listOf(outcome.datePairIndex) else emptyList()
        }).filter { it in 0 until requestedPairCount }.toSet()
        val attemptedIndices = (outcome.datePairAttemptedIndices.ifEmpty {
            if (outcome.datePairScanned) listOf(outcome.datePairIndex) else emptyList()
        }).filter { it in 0 until requestedPairCount }.distinct()
        val resetFlexibleCycle = alert.flexibleDateSearch && scannedIndices.isNotEmpty() && sameAttemptCriteria &&
            (previous.datePairCount != requestedPairCount || hasCompletedDatePairCycle(previous))
        val storedPairCount = if (alert.flexibleDateSearch && scannedIndices.isEmpty() && sameAttemptCriteria) {
            previous.datePairCount
        } else requestedPairCount
        val priorOffers = if (alert.flexibleDateSearch && sameVerifiedCriteria && !resetFlexibleCycle) {
            previous.verifiedOffersJson?.let { runCatching { kotlinx.serialization.json.Json.decodeFromString<List<com.flightnotifications.app.domain.model.VerifiedRoundTripOffer>>(it) }.getOrNull() }.orEmpty()
        } else emptyList()
        val cycleOffers = if (alert.flexibleDateSearch) {
            (priorOffers + outcome.offers).distinctBy { it.fingerprint }.sortedBy { it.totalCop }.take(MAX_ACCUMULATED_DATE_OFFERS)
        } else {
            outcome.offers.distinctBy { it.fingerprint }.sortedBy { it.totalCop }
        }
        val bestOffer = cycleOffers.minByOrNull { it.totalCop }
        val progressMaskBefore = if (
            alert.flexibleDateSearch && sameAttemptCriteria && !resetFlexibleCycle && previous.datePairCount == storedPairCount
        ) previous.datePairProgressMask else 0L
        val progressMask = if (alert.flexibleDateSearch) {
            scannedIndices.fold(progressMaskBefore) { mask, index -> mask or (1L shl index) }
        } else progressMaskBefore
        val pairsChecked = if (alert.flexibleDateSearch) java.lang.Long.bitCount(progressMask) else 1
        val incompleteMaskBefore = if (
            alert.flexibleDateSearch && sameAttemptCriteria && !resetFlexibleCycle && previous.datePairCount == storedPairCount
        ) previous.datePairIncompleteMask else 0L
        val incompleteMask = if (alert.flexibleDateSearch) {
            scannedIndices.fold(incompleteMaskBefore) { mask, index ->
                val bit = 1L shl index
                if (index in incompleteIndices) mask or bit else mask and bit.inv()
            }
        } else incompleteMaskBefore
        val persistedStatus = if (alert.flexibleDateSearch && bestOffer != null) "VERIFICADO" else outcome.status
        dao.putResult(
            FlightAlertResultEntity(
                alertId = alertId,
                status = persistedStatus,
                lastAttemptAt = outcome.attemptAtMillis,
                attemptCriteria = criteria,
                attemptMaximumCop = alert.maxTotalCop,
                networkReceivedAt = outcome.networkReceivedAtMillis,
                lastError = outcome.error,
                lastVerifiedTotalCop = bestOffer?.totalCop ?: if (resetFlexibleCycle) null else previous?.lastVerifiedTotalCop,
                lastVerifiedAt = verified?.let { outcome.networkReceivedAtMillis }
                    ?: if (resetFlexibleCycle) null else previous?.lastVerifiedAt,
                verifiedCriteria = if (bestOffer != null) criteria else if (resetFlexibleCycle) null else previous?.verifiedCriteria,
                selectedOfferJson = bestOffer?.let { kotlinx.serialization.json.Json.encodeToString(it) }
                    ?: if (resetFlexibleCycle) null else previous?.selectedOfferJson,
                airlinesJson = kotlinx.serialization.json.Json.encodeToString(outcome.airlines),
                candidatesConsidered = outcome.candidatesConsidered,
                candidateLimit = outcome.candidateLimit,
                explorationLimited = if (alert.flexibleDateSearch) {
                    pairsChecked < storedPairCount || incompleteMask != 0L
                } else outcome.explorationLimited,
                accountBalanceAtStart = outcome.accountBalanceAtStart,
                flightGetsStarted = outcome.flightGetsStarted,
                googleFlightsUrl = outcome.googleFlightsUrl,
                verifiedOffersJson = cycleOffers.takeIf { it.isNotEmpty() }?.let { kotlinx.serialization.json.Json.encodeToString(it) }
                    ?: if (resetFlexibleCycle) null else previous?.verifiedOffersJson,
                datePairsChecked = pairsChecked,
                datePairCount = storedPairCount,
                datePairProgressMask = progressMask,
                datePairIncompleteMask = incompleteMask,
                candidatesCompleted = outcome.candidatesCompleted,
                candidateFailures = outcome.candidateFailures,
                accountAlias = outcome.accountAlias,
            ),
        )
        return true
    }

    suspend fun recordManualAttempt(alert: FlightAlertEntity, attemptAt: Long) =
        dao.recordManualAttemptIfCurrent(alert.id, alert.criteriaVersion, attemptAt)

    suspend fun recordAutomaticAttempt(alert: FlightAlertEntity, attemptAt: Long, nextDueAt: Long) =
        dao.recordAutomaticAttemptIfCurrent(alert.id, alert.criteriaVersion, attemptAt, nextDueAt)

    suspend fun advanceCandidateRotation(alert: FlightAlertEntity) =
        dao.advanceCandidateRotation(alert.id, alert.criteriaVersion)

    suspend fun advanceDatePairRotation(alert: FlightAlertEntity, outcome: AlertSearchResult) {
        if (outcome.datePairCount > 1) {
            val attempted = outcome.datePairAttemptedIndices.ifEmpty {
                if (outcome.datePairScanned) listOf(outcome.datePairIndex) else emptyList()
            }
            attempted.forEach { index ->
                dao.advanceDatePairRotation(alert.id, alert.criteriaVersion, index, outcome.datePairCount)
            }
        }
    }

    private fun hasCompletedDatePairCycle(result: FlightAlertResultEntity): Boolean {
        val count = result.datePairCount.coerceIn(1, 49)
        val completeMask = (1L shl count) - 1L
        return (result.datePairProgressMask and completeMask) == completeMask
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val alert = dao.getAlert(id) ?: return
        val now = System.currentTimeMillis()
        val nextDue = when {
            !enabled -> null
            else -> maxOf(now, alert.nextDueAt ?: now)
        }
        dao.setEnabled(id, enabled, now, nextDue)
    }

    fun newAlert(
        name: String, origin: String, destination: String, outbound: LocalDate, returning: LocalDate?,
        maximum: Long, frequency: Int, direct: Boolean, enabled: Boolean,
        adults18Plus: Int = 1, minors: List<MinorPassenger> = emptyList(),
    ): FlightAlertEntity {
        val now = System.currentTimeMillis()
        return FlightAlertEntity(
            id = UUID.randomUUID().toString(), name = name.trim(), originIata = origin.trim().uppercase(),
            destinationIata = destination.trim().uppercase(), outboundDate = outbound.toString(), returnDate = returning?.toString().orEmpty(),
            maxTotalCop = maximum, frequencyHours = frequency, directOnly = direct, enabled = enabled,
            createdAt = now, updatedAt = now, nextDueAt = if (enabled) now else null,
            adults18Plus = adults18Plus, minorPassengersJson = Json.encodeToString(minors),
        )
    }

    private fun alertCriteriaKey(alert: FlightAlertEntity): String = if (!alert.flexibleDateSearch) {
        "${alert.originIata}-${alert.destinationIata}|${alert.outboundDate}|${alert.returnDate.ifBlank { "one-way" }}|direct=${alert.directOnly}|adults=${alert.adults18Plus}|minors=${alert.minorPassengersJson}"
    } else {
        val outboundEnd = alert.outboundDateEnd.ifBlank { alert.outboundDate }
        val returnEnd = alert.returnDateEnd.ifBlank { alert.returnDate }
        "${alert.originIata}-${alert.destinationIata}|${alert.outboundDate}..$outboundEnd|${if (alert.returnDate.isBlank()) "one-way" else "${alert.returnDate}..$returnEnd"}|direct=${alert.directOnly}|adults=${alert.adults18Plus}|minors=${alert.minorPassengersJson}|flexible=true"
    }

    private companion object {
        const val MAX_ACCUMULATED_DATE_OFFERS = 12
    }
}
