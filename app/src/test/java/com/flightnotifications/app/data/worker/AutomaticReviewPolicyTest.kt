package com.flightnotifications.app.data.worker

import com.flightnotifications.app.data.local.FlightAlertEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticReviewPolicyTest {
    @Test
    fun doesNotRunWithoutDueAlertsOrDeliverableNotifications() {
        assertEquals(AutomaticReviewPolicy.Decision.NO_DUE_ALERTS, AutomaticReviewPolicy.decide(0, false))
        assertEquals(AutomaticReviewPolicy.Decision.NOTIFICATIONS_BLOCKED, AutomaticReviewPolicy.decide(1, false))
        assertEquals(AutomaticReviewPolicy.Decision.RUN, AutomaticReviewPolicy.decide(1, true))
    }

    @Test
    fun reviewCompletedWhileWaitingCoalescesOnlyTheOverlappingManualOrWorkerTick() {
        val completedWhileWaiting = alert(lastReviewCompletedAt = 15_000)
        val olderReview = alert(lastReviewCompletedAt = 9_999)
        val noReview = alert(lastReviewCompletedAt = null)

        assertTrue(AutomaticReviewPolicy.wasReviewedAfter(completedWhileWaiting, 10_000))
        assertFalse(AutomaticReviewPolicy.wasReviewedAfter(olderReview, 10_000))
        assertFalse(AutomaticReviewPolicy.wasReviewedAfter(noReview, 10_000))
        assertEquals(listOf(olderReview, noReview), AutomaticReviewPolicy.excludeReviewedSince(listOf(completedWhileWaiting, olderReview, noReview), 10_000))
        assertFalse(AutomaticReviewPolicy.wasReviewedAfter(alert(lastReviewCompletedAt = 100_000_000), 10_000))
    }

    private fun alert(lastReviewCompletedAt: Long?) = FlightAlertEntity(
        id = "test-alert",
        name = "Test",
        originIata = "CTG",
        destinationIata = "CLO",
        outboundDate = "2027-03-25",
        returnDate = "2027-03-28",
        maxTotalCop = 500_000,
        frequencyHours = 24,
        createdAt = 1,
        updatedAt = 1,
        lastReviewCompletedAt = lastReviewCompletedAt,
    )
}
