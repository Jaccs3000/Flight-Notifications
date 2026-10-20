package com.flightnotifications.app.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flightnotifications.app.FlightProbeApplication
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.notification.FlightNotificationDispatcher
import com.flightnotifications.app.domain.model.AlertSearchResult
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.ZoneId

class FlightAlertWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val container get() = (applicationContext as FlightProbeApplication).container

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        return try {
            // Recover any notice interrupted between NotificationManager.notify() and its Room commit.
            // This runs before new searches and does not consume SerpApi requests.
            val recoveredNotifications = container.notificationDispatcher.retryPending(container.flightAlertRepository)
            if (recoveredNotifications.isNotEmpty()) {
                val counts = recoveredNotifications.groupingBy { it.name }.eachCount().toSortedMap()
                    .entries.joinToString(",") { "${it.key}:${it.value}" }
                container.flightDiagnosticLog.record("notification_outbox_recovery processed=${recoveredNotifications.size} outcomes={$counts}")
            }
            var decision = AutomaticReviewPolicy.Decision.NO_DUE_ALERTS
            var expiredCount = 0
            var dueCount = 0
            var coalescedCount = 0
            var submittedAlerts = emptyList<FlightAlertEntity>()
            container.serpApiRepository.runAlertBatch(
                alerts = emptyList(),
                afterResults = { outcomes ->
                    persistAndDispatch(outcomes, submittedAlerts.associateBy { it.id })
                },
                selectAlerts = {
                    // Re-read state after acquiring the repository's execution lock. This avoids
                    // running a stale worker snapshot after a manual review, pause, edit, or delete.
                    val today = LocalDate.now(COLOMBIA_ZONE)
                    val expired = container.flightAlertRepository.enabledAlerts()
                        .filter { today.isAfter(LocalDate.parse(it.outboundDate)) }
                    expired.forEach {
                        container.flightAlertRepository.deferExpired(it.id)
                        container.flightAlertRepository.clearExpiredNotificationState(it.id)
                    }
                    expiredCount = expired.size
                    val due = container.flightAlertRepository.dueAlerts(System.currentTimeMillis())
                        .filterNot { today.isAfter(LocalDate.parse(it.outboundDate)) }
                    val actionableDue = AutomaticReviewPolicy.excludeReviewedSince(due, now)
                    dueCount = actionableDue.size
                    coalescedCount = due.size - actionableDue.size
                    decision = AutomaticReviewPolicy.decide(actionableDue.size, container.notificationDispatcher.notificationsAllowed())
                    if (decision == AutomaticReviewPolicy.Decision.RUN) {
                        submittedAlerts = actionableDue
                        container.flightDiagnosticLog.record("scheduler_tick due_alerts=${actionableDue.size} coalesced_alerts=$coalescedCount worker_run_attempt=$runAttemptCount max_batch_gets=${com.flightnotifications.app.data.repository.SerpApiRepository.MAX_GETS_PER_BATCH}")
                        actionableDue
                    } else {
                        emptyList()
                    }
                },
            )
            when (decision) {
                AutomaticReviewPolicy.Decision.NO_DUE_ALERTS ->
                    container.flightDiagnosticLog.record("scheduler_tick no_due_alerts due_alerts=$dueCount coalesced_alerts=$coalescedCount expired_alerts=$expiredCount flight_gets_started=0")
                AutomaticReviewPolicy.Decision.NOTIFICATIONS_BLOCKED ->
                    container.flightDiagnosticLog.record("scheduler_tick blocked cause=notification_permission_or_channel_disabled due_alerts=$dueCount expired_alerts=$expiredCount flight_gets_started=0")
                AutomaticReviewPolicy.Decision.RUN -> Unit
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            runCatching {
                container.flightDiagnosticLog.record("scheduler_worker_failed category=${failure.javaClass.simpleName}")
            }
            Result.failure()
        }
    }

    private suspend fun persistAndDispatch(
        outcomes: List<AlertSearchResult>,
        submitted: Map<String, FlightAlertEntity>,
    ) {
        outcomes.forEach { outcome ->
            val original = submitted[outcome.alertId] ?: return@forEach
            val current = container.flightAlertRepository.find(original.id) ?: return@forEach
            val saved = container.flightAlertRepository.saveOutcome(original.id, outcome, original)
            if (!saved) {
                container.flightDiagnosticLog.record("scheduler_result_discarded alert_id=${original.id.take(8)} reason=criteria_version_changed")
                return@forEach
            }
            if (outcome.candidatesConsidered > 0) container.flightAlertRepository.advanceCandidateRotation(original)
            if (current.enabled && current.criteriaVersion == original.criteriaVersion && current.notificationPolicyVersion == original.notificationPolicyVersion && outcome.status == "VERIFICADO") {
                val dispatched = container.notificationDispatcher.dispatchIfEligible(current, outcome, container.flightAlertRepository)
                container.flightDiagnosticLog.record("notification_dispatch alert_id=${original.id.take(8)} result=$dispatched")
            } else if (outcome.status == "VERIFICADO" && !container.notificationDispatcher.notificationsAllowed()) {
                container.flightDiagnosticLog.record("notification_suppressed alert_id=${original.id.take(8)} cause=permission_or_channel_disabled")
            }
            val completedAt = System.currentTimeMillis()
            val delay = AlertRetryPolicy.nextDelayMillis(current.frequencyHours, outcome.error)
            val updated = container.flightAlertRepository.recordAutomaticAttempt(
                alert = current,
                attemptAt = outcome.attemptAtMillis,
                nextDueAt = completedAt + delay,
                completedAt = completedAt,
            )
            container.flightDiagnosticLog.record("scheduler_result_saved alert_id=${original.id.take(8)} status=${outcome.status} saved=$saved schedule_updated=${updated > 0} next_due_in_ms=$delay flight_gets=${outcome.flightGetsStarted}")
        }
    }

    companion object {
        private val COLOMBIA_ZONE = ZoneId.of("America/Bogota")
    }
}
