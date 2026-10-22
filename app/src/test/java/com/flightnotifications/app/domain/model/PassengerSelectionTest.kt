package com.flightnotifications.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PassengerSelectionTest {
    @Test
    fun mapsEveryAgeGroupToThePassengerCountsSupportedByGoogleFlights() {
        val counts = PassengerSelection.counts(
            adults18Plus = 2,
            minors = listOf(
                MinorPassenger(17),
                MinorPassenger(12),
                MinorPassenger(11),
                MinorPassenger(2),
                MinorPassenger(0, "lap"),
                MinorPassenger(1, "seat"),
            ),
        )

        assertEquals(PassengerCounts(adults = 4, children = 2, infantsInSeat = 1, infantsOnLap = 1), counts)
    }

    @Test
    fun acceptsOneWayAndRoundTripPassengersButRequiresInfantTravelMode() {
        assertNull(PassengerSelection.validationError(1, emptyList()))
        assertNull(PassengerSelection.validationError(2, listOf(MinorPassenger(16), MinorPassenger(7))))
        assertEquals(
            "Indica si cada bebé menor de 2 años viajará en brazos o en su propio asiento.",
            PassengerSelection.validationError(1, listOf(MinorPassenger(1))),
        )
    }

    @Test
    fun limitsPassengerCountAndLapInfantsPerAdult() {
        assertEquals(
            "Google Flights permite consultar hasta 9 pasajeros por búsqueda.",
            PassengerSelection.validationError(2, List(8) { MinorPassenger(8) }),
        )
        assertEquals(
            "Debe viajar un adulto responsable por cada bebé que vaya en brazos.",
            PassengerSelection.validationError(1, listOf(MinorPassenger(0, "lap"), MinorPassenger(1, "lap"))),
        )
    }
}
