package com.flightnotifications.app.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferNotificationPolicyTest {
    @Test
    fun maximumIsInclusiveAndOfferEligibilityDoesNotDependOnPastNotifications() {
        assertTrue(OfferNotificationPolicy.isEligible(350_000, 350_000))
        assertFalse(OfferNotificationPolicy.isEligible(350_001, 350_000))
        assertTrue(OfferNotificationPolicy.isEligible(400_000, 450_000))
    }

    @Test
    fun samePriceRemainsEligibleOnEveryReview() {
        assertTrue(OfferNotificationPolicy.isEligible(350_000, 500_000))
        assertTrue(OfferNotificationPolicy.isEligible(350_000, 500_000))
    }

    @Test
    fun nonPositiveMaximumOrPriceCannotNotify() {
        assertFalse(OfferNotificationPolicy.isEligible(0, 500_000))
        assertFalse(OfferNotificationPolicy.isEligible(100, 0))
    }

    @Test
    fun selectsExactlyOneCheapestEligibleOfferAndPreservesStableTies() {
        val expensive = OfferNotificationPolicy.EligibleOffer("expensive", 400_000)
        val cheapestFirst = OfferNotificationPolicy.EligibleOffer("cheapest-first", 350_000)
        val cheapestSecond = OfferNotificationPolicy.EligibleOffer("cheapest-second", 350_000)

        assertTrue(
            OfferNotificationPolicy.selectCheapestEligible(listOf(expensive, cheapestFirst, cheapestSecond)) === cheapestFirst,
        )
        assertNull(OfferNotificationPolicy.selectCheapestEligible(listOf(OfferNotificationPolicy.EligibleOffer("invalid", 0))))
    }
}
