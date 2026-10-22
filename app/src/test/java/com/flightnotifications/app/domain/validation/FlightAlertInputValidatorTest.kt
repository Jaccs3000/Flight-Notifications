package com.flightnotifications.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.flightnotifications.app.domain.model.MinorPassenger
import java.time.LocalDate

class FlightAlertInputValidatorTest {
    private val today = LocalDate.parse("2026-10-20")
    private val domesticAirports = setOf("CTG", "CLO", "BOG", "MDE")

    @Test
    fun acceptsMinimumAndMaximumNameLengthsAndEveryApprovedFrequency() {
        for (name in listOf("ABC", "A".repeat(80))) {
            for (frequency in FlightAlertInputValidator.ALLOWED_FREQUENCIES) {
                assertNull(validate(name = name, frequency = frequency))
            }
        }
    }

    @Test
    fun acceptsOneWayAndRequiresAgeAndInfantSeatChoiceForMinors() {
        assertNull(validate(returning = null))
        assertNull(validate(returning = null, minors = listOf(MinorPassenger(17), MinorPassenger(8))))
        assertEquals(
            "Indica si cada bebé menor de 2 años viajará en brazos o en su propio asiento.",
            validate(minors = listOf(MinorPassenger(0))),
        )
    }

    @Test
    fun rejectsEveryInvalidAlertFieldWithItsSpecificMessage() {
        assertEquals("El nombre debe tener entre 3 y 80 caracteres.", validate(name = "AB"))
        assertEquals("El nombre debe tener entre 3 y 80 caracteres.", validate(name = "A".repeat(81)))
        assertEquals(
            "El origen debe ser un aeropuerto colombiano del catálogo local.",
            validate(origin = "XXX"),
        )
        assertEquals(
            "El destino debe ser un aeropuerto colombiano del catálogo local.",
            validate(destination = "XXX"),
        )
        assertEquals("El origen y el destino deben ser distintos.", validate(origin = "ctg", destination = "CTG"))
        assertEquals("La fecha de ida no puede estar en el pasado.", validate(outbound = today.minusDays(1)))
        assertEquals("La fecha de regreso debe ser posterior a la ida.", validate(returning = today))
        assertEquals("El máximo total debe ser un valor positivo en COP.", validate(maximum = 0))
        assertEquals("El máximo total debe ser un valor positivo en COP.", validate(maximum = -1))
        assertEquals("Frecuencia no permitida.", validate(frequency = 7))
    }

    private fun validate(
        name: String = "Viaje Cali",
        origin: String = "CTG",
        destination: String = "CLO",
        outbound: LocalDate = today,
        returning: LocalDate? = today.plusDays(2),
        maximum: Long = 500_000,
        frequency: Int = 24,
        adults18Plus: Int = 1,
        minors: List<MinorPassenger> = emptyList(),
    ) = FlightAlertInputValidator.validate(
        name = name,
        origin = origin,
        destination = destination,
        outboundDate = outbound,
        returnDate = returning,
        maximumCop = maximum,
        frequencyHours = frequency,
        today = today,
        containsDomesticAirport = { code -> code.trim().uppercase() in domesticAirports },
        adults18Plus = adults18Plus,
        minors = minors,
    )
}
