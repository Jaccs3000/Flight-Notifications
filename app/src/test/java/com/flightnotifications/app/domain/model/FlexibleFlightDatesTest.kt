package com.flightnotifications.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FlexibleFlightDatesTest {
    private val base = LocalDate.parse("2027-03-25")

    @Test
    fun createsAllValidRoundTripCombinationsInStableOrder() {
        val pairs = FlexibleFlightDates.allPairs(
            outboundStart = base,
            outboundEnd = base.plusDays(1),
            returnStart = base.plusDays(2),
            returnEnd = base.plusDays(3),
        )

        assertEquals(4, pairs.size)
        assertEquals(
            listOf(
                base to base.plusDays(2),
                base to base.plusDays(3),
                base.plusDays(1) to base.plusDays(2),
                base.plusDays(1) to base.plusDays(3),
            ),
            pairs.map { it.outbound to it.returning },
        )
    }

    @Test
    fun excludesDeparturesInThePastAndReturnsNotAfterDeparture() {
        val pairs = FlexibleFlightDates.pairs(
            outboundStart = base,
            outboundEnd = base.plusDays(2),
            returnStart = base.plusDays(1),
            returnEnd = base.plusDays(2),
            todayAtOrigin = base.plusDays(1),
        )

        assertEquals(1, pairs.size)
        assertEquals(base.plusDays(1), pairs.single().outbound)
        assertEquals(base.plusDays(2), pairs.single().returning)
    }

    @Test
    fun createsOneWayDatePairsAndRejectsInvalidOrOverlongWindows() {
        val oneWay = FlexibleFlightDates.allPairs(base, base.plusDays(2), null, null)
        assertEquals(3, oneWay.size)
        assertTrue(oneWay.all { it.returning == null })
        assertTrue(FlexibleFlightDates.allPairs(base.plusDays(2), base, null, null).isEmpty())
        assertTrue(FlexibleFlightDates.allPairs(base, base.plusDays(49), null, null).isEmpty())
    }

    @Test
    fun supportsTheMaximumOfFortyNineValidRoundTripPairs() {
        val pairs = FlexibleFlightDates.allPairs(
            outboundStart = base,
            outboundEnd = base.plusDays(6),
            returnStart = base.plusDays(7),
            returnEnd = base.plusDays(13),
        )

        assertEquals(FlexibleFlightDates.MAX_DATE_PAIRS, pairs.size)
        assertEquals(FlightDatePair(base, base.plusDays(7)), pairs.first())
        assertEquals(FlightDatePair(base.plusDays(6), base.plusDays(13)), pairs.last())
    }
}
