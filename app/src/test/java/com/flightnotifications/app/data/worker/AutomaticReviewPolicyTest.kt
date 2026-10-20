package com.flightnotifications.app.data.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticReviewPolicyTest {
    @Test
    fun scheduledSearchRequiresDueAlertsAndNotificationPermission() {
        assertEquals(
            AutomaticReviewPolicy.Decision.NO_DUE_ALERTS,
            AutomaticReviewPolicy.decide(dueAlertCount = 0, notificationsAllowed = false),
        )
        assertEquals(
            AutomaticReviewPolicy.Decision.NOTIFICATIONS_BLOCKED,
            AutomaticReviewPolicy.decide(dueAlertCount = 1, notificationsAllowed = false),
        )
        assertEquals(
            AutomaticReviewPolicy.Decision.RUN,
            AutomaticReviewPolicy.decide(dueAlertCount = 1, notificationsAllowed = true),
        )
    }
}
