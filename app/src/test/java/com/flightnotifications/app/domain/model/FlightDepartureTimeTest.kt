package com.flightnotifications.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightDepartureTimeTest {
    @Test
    fun formatsStoredIsoAndLegacySpaceSeparatedLocalTimes() {
        assertEquals("06:30", FlightDepartureTime.format("2027-03-25T06:30"))
        assertEquals("18:45", FlightDepartureTime.format("2027-03-28 18:45"))
        assertEquals("09:10", FlightDepartureTime.format("2027-03-25T09:10:00"))
    }

    @Test
    fun missingOrInvalidTimesAreOmitted() {
        assertNull(FlightDepartureTime.format(null))
        assertNull(FlightDepartureTime.format(" "))
        assertNull(FlightDepartureTime.format("hora no disponible"))
    }
}
