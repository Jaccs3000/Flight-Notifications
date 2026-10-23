package com.flightnotifications.app.data.worker

/** Scheduled searches still refresh prices even when Android notification delivery is unavailable. */
object AutomaticReviewPolicy {
    enum class Decision { NO_DUE_ALERTS, RUN }

    fun decide(dueAlertCount: Int): Decision =
        if (dueAlertCount <= 0) Decision.NO_DUE_ALERTS else Decision.RUN
}
