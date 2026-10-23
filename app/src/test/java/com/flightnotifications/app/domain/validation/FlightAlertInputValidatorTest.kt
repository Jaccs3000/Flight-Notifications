package com.flightnotifications.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.flightnotifications.app.domain.model.MinorPassenger
import java.time.LocalDate

class FlightAlertInputValidatorTest {
    private val today = LocalDate.parse("2026-10-20")
    private val knownAirports = setOf("CTG", "CLO", "BOG", "MDE", "JFK", "LHR", "NRT")

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
            "Selecciona un aeropuerto válido del catálogo mundial para el origen.",
            validate(origin = "XXX"),
        )
        assertEquals(
            "Selecciona un aeropuerto válido del catálogo mundial para el destino.",
            validate(destination = "XXX"),
        )
        assertEquals("El origen y el destino deben ser distintos.", validate(origin = "ctg", destination = "CTG"))
        assertEquals("La fecha de ida no puede estar en el pasado.", validate(outbound = today.minusDays(1)))
        assertEquals("La fecha de regreso debe ser posterior a la ida.", validate(returning = today))
        assertEquals("El máximo total debe ser un valor positivo en COP.", validate(maximum = 0))
        assertEquals("El máximo total debe ser un valor positivo en COP.", validate(maximum = -1))
        assertEquals("Frecuencia no permitida.", validate(frequency = 7))
    }

    @Test
    fun acceptsAnInternationalOriginAndDestinationFromTheGlobalCatalog() {
        assertNull(validate(origin = "JFK", destination = "LHR"))
        assertNull(validate(origin = "NRT", destination = "CTG"))
    }

    @Test
    fun acceptsFlexibleDateWindowsAndRejectsInvalidOrOverBudgetCombinations() {
        assertNull(
            validate(
                flexible = true,
                outbound = today.plusDays(5),
                outboundEnd = today.plusDays(6),
                returning = today.plusDays(7),
                returnEnd = today.plusDays(8),
            ),
        )
        assertEquals(
            "El final del rango de ida no puede ser anterior al inicio.",
            validate(flexible = true, outbound = today.plusDays(5), outboundEnd = today.plusDays(4)),
        )
        assertEquals(
            "El rango debe incluir al menos una combinación con regreso posterior a la ida.",
            validate(
                flexible = true,
                outbound = today.plusDays(5),
                outboundEnd = today.plusDays(6),
                returning = today.plusDays(3),
                returnEnd = today.plusDays(4),
            ),
        )
        assertEquals(
            "El rango puede incluir hasta 49 combinaciones de fechas. Reduce alguno de los rangos.",
            validate(
                flexible = true,
                outbound = today.plusDays(5),
                outboundEnd = today.plusDays(12),
                returning = today.plusDays(13),
                returnEnd = today.plusDays(20),
            ),
        )
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
        flexible: Boolean = false,
        outboundEnd: LocalDate = outbound,
        returnEnd: LocalDate? = returning,
    ) = FlightAlertInputValidator.validate(
        name = name,
        origin = origin,
        destination = destination,
        outboundDate = outbound,
        returnDate = returning,
        maximumCop = maximum,
        frequencyHours = frequency,
        today = today,
        containsAirport = { code -> code.trim().uppercase() in knownAirports },
        adults18Plus = adults18Plus,
        minors = minors,
        flexibleDateSearch = flexible,
        outboundDateEnd = outboundEnd,
        returnDateEnd = returnEnd,
    )
}
