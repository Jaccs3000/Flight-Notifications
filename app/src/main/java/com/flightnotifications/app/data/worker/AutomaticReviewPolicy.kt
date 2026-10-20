package com.flightnotifications.app.data.worker

import com.flightnotifications.app.data.local.FlightAlertEntity

/** Avoids spending provider quota on scheduled reviews whose notices cannot be delivered. */
object AutomaticReviewPolicy {
    enum class Decision { NO_DUE_ALERTS, NOTIFICATIONS_BLOCKED, RUN }

    fun decide(dueAlertCount: Int, notificationsAllowed: Boolean): Decision = when {
        dueAlertCount <= 0 -> Decision.NO_DUE_ALERTS
        !notificationsAllowed -> Decision.NOTIFICATIONS_BLOCKED
        else -> Decision.RUN
    }

    /** A review completed while this request waited on the shared API lock can satisfy that tick. */
    fun wasReviewedAfter(alert: FlightAlertEntity, requestedAt: Long): Boolean =
        alert.lastReviewCompletedAt?.let { completedAt ->
            completedAt >= requestedAt && completedAt - requestedAt <= COALESCE_WINDOW_MILLIS
        } == true

    fun excludeReviewedSince(alerts: List<FlightAlertEntity>, tickStartedAt: Long): List<FlightAlertEntity> =
        alerts.filterNot { wasReviewedAfter(it, tickStartedAt) }

    private const val COALESCE_WINDOW_MILLIS = 6L * 60L * 60L * 1_000L
}
