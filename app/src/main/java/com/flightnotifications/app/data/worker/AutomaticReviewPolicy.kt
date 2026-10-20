package com.flightnotifications.app.data.worker

/** Avoids spending provider quota on scheduled reviews that cannot deliver notifications. */
object AutomaticReviewPolicy {
    enum class Decision { NO_DUE_ALERTS, NOTIFICATIONS_BLOCKED, RUN }

    fun decide(dueAlertCount: Int, notificationsAllowed: Boolean): Decision = when {
        dueAlertCount <= 0 -> Decision.NO_DUE_ALERTS
        !notificationsAllowed -> Decision.NOTIFICATIONS_BLOCKED
        else -> Decision.RUN
    }
}
