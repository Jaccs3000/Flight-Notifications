package com.flightnotifications.app.data.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticReviewPolicyTest {
    @Test
    fun scheduledSearchRequiresDueAlertsButNotNotificationPermission() {
        assertEquals(
            AutomaticReviewPolicy.Decision.NO_DUE_ALERTS,
            AutomaticReviewPolicy.decide(dueAlertCount = 0),
        )
        assertEquals(
            AutomaticReviewPolicy.Decision.RUN,
            AutomaticReviewPolicy.decide(dueAlertCount = 1),
        )
    }
}
