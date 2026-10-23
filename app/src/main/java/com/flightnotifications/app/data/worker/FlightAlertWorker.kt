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
import java.time.Instant

class FlightAlertWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val container get() = (applicationContext as FlightProbeApplication).container

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val workerRunId = id.toString()
        val trigger = inputData.getString("trigger") ?: "existing_work"
        var finalState = "running"
        container.flightDiagnosticLog.record(
            "scheduler_worker_started worker_run_id=$workerRunId trigger=$trigger attempt=$runAttemptCount required_network=CONNECTED started_at_ms=$now",
        )
        return try {
            // Recover any notice interrupted between NotificationManager.notify() and its Room commit.
            // This runs before new searches and does not consume SerpApi requests.
            val recoveredNotifications = container.notificationDispatcher.retryPending(container.flightAlertRepository)
            if (recoveredNotifications.isNotEmpty()) {
                val counts = recoveredNotifications.groupingBy { it.name }.eachCount().toSortedMap()
                    .entries.joinToString(",") { "${it.key}:${it.value}" }
                container.flightDiagnosticLog.record("notification_outbox_recovery processed=${recoveredNotifications.size} outcomes={$counts}")
            }
            // Expire enabled alerts at the first coordinator run after the local departure day,
            // even when their next scheduled review would otherwise be later.
            val expired = container.flightAlertRepository.enabledAlerts()
                .filter { alert ->
                    val zone = container.airportCatalog.timeZoneFor(alert.originIata) ?: COLOMBIA_ZONE
                    val finalOutboundDate = if (alert.flexibleDateSearch) alert.outboundDateEnd.ifBlank { alert.outboundDate } else alert.outboundDate
                    Instant.ofEpochMilli(now).atZone(zone).toLocalDate().isAfter(LocalDate.parse(finalOutboundDate))
                }
            expired.forEach {
                container.flightAlertRepository.deferExpired(it.id)
                container.flightAlertRepository.clearExpiredNotificationState(it.id)
            }
            val due = container.flightAlertRepository.dueAlerts(now)
            val expiredIds = expired.mapTo(mutableSetOf()) { it.id }
            val activeDue = due.filterNot { it.id in expiredIds }
            when (AutomaticReviewPolicy.decide(activeDue.size)) {
                AutomaticReviewPolicy.Decision.NO_DUE_ALERTS -> {
                    container.flightDiagnosticLog.record("scheduler_tick worker_run_id=$workerRunId no_due_alerts expired_alerts=${expired.size} flight_gets_started=0")
                    finalState = "success_no_due_alerts"
                    return Result.success()
                }
                AutomaticReviewPolicy.Decision.RUN -> Unit
            }
            container.flightDiagnosticLog.record("scheduler_tick worker_run_id=$workerRunId due_alerts=${activeDue.size} worker_run_attempt=$runAttemptCount max_batch_gets=${com.flightnotifications.app.data.repository.SerpApiRepository.MAX_GETS_PER_BATCH}")
            val byId = activeDue.associateBy { it.id }
            container.serpApiRepository.runAlertBatch(activeDue, source = "scheduled_$trigger", afterResults = { outcomes ->
                persistAndDispatch(outcomes, byId)
            })
            finalState = "success_search_batch_complete"
            Result.success()
        } catch (cancelled: CancellationException) {
            finalState = "cancelled"
            throw cancelled
        } catch (failure: Exception) {
            finalState = "failure_${failure.javaClass.simpleName}"
            runCatching {
                container.flightDiagnosticLog.record("scheduler_worker_failed worker_run_id=$workerRunId category=${failure.javaClass.simpleName}")
            }
            Result.failure()
        } finally {
            runCatching {
                container.flightDiagnosticLog.record(
                    "scheduler_worker_finished worker_run_id=$workerRunId trigger=$trigger result=$finalState duration_ms=${(System.currentTimeMillis() - now).coerceAtLeast(0)}",
                )
            }
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
            if (outcome.flightGetsStarted > 0) container.flightAlertRepository.advanceCandidateRotation(original)
            container.flightAlertRepository.advanceDatePairRotation(original, outcome)
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
            )
            container.flightDiagnosticLog.record("scheduler_result_saved alert_id=${original.id.take(8)} status=${outcome.status} saved=$saved schedule_updated=${updated > 0} next_due_in_ms=$delay flight_gets=${outcome.flightGetsStarted}")
        }
    }

    companion object {
        private val COLOMBIA_ZONE = ZoneId.of("America/Bogota")
    }
}
