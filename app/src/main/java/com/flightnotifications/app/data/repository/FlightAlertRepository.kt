package com.flightnotifications.app.data.repository

import com.flightnotifications.app.data.local.FlightAlertDao
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.local.FlightAlertResultEntity
import com.flightnotifications.app.data.local.SentNotificationEntity
import com.flightnotifications.app.data.local.NotificationOutboxEntity
import com.flightnotifications.app.data.notification.NotificationOutboxStore
import com.flightnotifications.app.domain.model.AlertSearchResult
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
                previous.directOnly != alert.directOnly
            )
        val maximumChanged = previous != null && previous.maxTotalCop != alert.maxTotalCop
        val frequencyChanged = previous != null && previous.frequencyHours != alert.frequencyHours
        val nextDueAt = if (alert.enabled && (routeChanged || maximumChanged || frequencyChanged)) System.currentTimeMillis() else alert.nextDueAt
        val saved = alert.copy(
            criteriaVersion = (previous?.criteriaVersion ?: alert.criteriaVersion) + if (routeChanged) 1 else 0,
            notificationPolicyVersion = (previous?.notificationPolicyVersion ?: alert.notificationPolicyVersion) +
                if (maximumChanged) 1 else 0,
            candidateRotation = previous?.candidateRotation ?: alert.candidateRotation,
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
        val criteria = "${alert.originIata}-${alert.destinationIata}|${alert.outboundDate}|${alert.returnDate}|direct=${alert.directOnly}"
        dao.putResult(
            FlightAlertResultEntity(
                alertId = alertId,
                status = outcome.status,
                lastAttemptAt = outcome.attemptAtMillis,
                attemptCriteria = criteria,
                attemptMaximumCop = alert.maxTotalCop,
                networkReceivedAt = outcome.networkReceivedAtMillis,
                lastError = outcome.error,
                lastVerifiedTotalCop = verified?.totalCop ?: previous?.lastVerifiedTotalCop,
                lastVerifiedAt = verified?.let { outcome.networkReceivedAtMillis } ?: previous?.lastVerifiedAt,
                verifiedCriteria = if (verified != null) criteria else previous?.verifiedCriteria,
                selectedOfferJson = verified?.let { kotlinx.serialization.json.Json.encodeToString(it) }
                    ?: previous?.selectedOfferJson,
                airlinesJson = kotlinx.serialization.json.Json.encodeToString(outcome.airlines),
                candidatesConsidered = outcome.candidatesConsidered,
                candidateLimit = outcome.candidateLimit,
                explorationLimited = outcome.explorationLimited,
                accountBalanceAtStart = outcome.accountBalanceAtStart,
                flightGetsStarted = outcome.flightGetsStarted,
                googleFlightsUrl = outcome.googleFlightsUrl,
                verifiedOffersJson = if (verified != null) {
                    kotlinx.serialization.json.Json.encodeToString(outcome.offers.distinctBy { it.fingerprint }.sortedBy { it.totalCop })
                } else {
                    previous?.verifiedOffersJson
                },
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

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val alert = dao.getAlert(id) ?: return
        val now = System.currentTimeMillis()
        val frequencyMillis = alert.frequencyHours * 60L * 60L * 1000L
        val nextDue = when {
            !enabled -> null
            alert.lastAutomaticAttemptAt != null -> maxOf(now, alert.lastAutomaticAttemptAt + frequencyMillis)
            else -> now
        }
        dao.setEnabled(id, enabled, now, nextDue)
    }

    fun newAlert(
        name: String, origin: String, destination: String, outbound: LocalDate, returning: LocalDate,
        maximum: Long, frequency: Int, direct: Boolean, enabled: Boolean,
    ): FlightAlertEntity {
        val now = System.currentTimeMillis()
        return FlightAlertEntity(
            id = UUID.randomUUID().toString(), name = name.trim(), originIata = origin.trim().uppercase(),
            destinationIata = destination.trim().uppercase(), outboundDate = outbound.toString(), returnDate = returning.toString(),
            maxTotalCop = maximum, frequencyHours = frequency, directOnly = direct, enabled = enabled,
            createdAt = now, updatedAt = now, nextDueAt = if (enabled) now else null,
        )
    }
}
