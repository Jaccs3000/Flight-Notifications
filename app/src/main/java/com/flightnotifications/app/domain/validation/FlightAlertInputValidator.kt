package com.flightnotifications.app.domain.validation

import java.time.LocalDate
import com.flightnotifications.app.domain.model.MinorPassenger
import com.flightnotifications.app.domain.model.PassengerSelection
import com.flightnotifications.app.domain.model.FlexibleFlightDates

/** Pure validation for the saved-alert form; the caller supplies the worldwide airport catalogue and local date. */
object FlightAlertInputValidator {
    val ALLOWED_FREQUENCIES = setOf(6, 8, 12, 24)

    fun validate(
        name: String,
        origin: String,
        destination: String,
        outboundDate: LocalDate,
        returnDate: LocalDate?,
        maximumCop: Long,
        frequencyHours: Int,
        today: LocalDate,
        containsAirport: (String) -> Boolean,
        adults18Plus: Int = 1,
        minors: List<MinorPassenger> = emptyList(),
        flexibleDateSearch: Boolean = false,
        outboundDateEnd: LocalDate = outboundDate,
        returnDateEnd: LocalDate? = returnDate,
    ): String? = when {
        name.trim().length !in 3..80 -> "El nombre debe tener entre 3 y 80 caracteres."
        !containsAirport(origin) -> "Selecciona un aeropuerto válido del catálogo mundial para el origen."
        !containsAirport(destination) -> "Selecciona un aeropuerto válido del catálogo mundial para el destino."
        origin.trim().equals(destination.trim(), ignoreCase = true) -> "El origen y el destino deben ser distintos."
        outboundDate.isBefore(today) -> "La fecha de ida no puede estar en el pasado."
        flexibleDateSearch && outboundDateEnd.isBefore(outboundDate) -> "El final del rango de ida no puede ser anterior al inicio."
        returnDate != null && flexibleDateSearch && (returnDateEnd == null || returnDateEnd.isBefore(returnDate)) -> "El final del rango de regreso no puede ser anterior al inicio."
        returnDate != null && !flexibleDateSearch && !returnDate.isAfter(outboundDate) -> "La fecha de regreso debe ser posterior a la ida."
        flexibleDateSearch && FlexibleFlightDates.allPairs(
            outboundDate,
            outboundDateEnd,
            returnDate,
            returnDateEnd,
        ).isEmpty() -> "El rango debe incluir al menos una combinación con regreso posterior a la ida."
        flexibleDateSearch && FlexibleFlightDates.allPairs(
            outboundDate,
            outboundDateEnd,
            returnDate,
            returnDateEnd,
        ).size > FlexibleFlightDates.MAX_DATE_PAIRS -> "El rango puede incluir hasta 49 combinaciones de fechas. Reduce alguno de los rangos."
        PassengerSelection.validationError(adults18Plus, minors) != null -> PassengerSelection.validationError(adults18Plus, minors)
        maximumCop <= 0L -> "El máximo total debe ser un valor positivo en COP."
        frequencyHours !in ALLOWED_FREQUENCIES -> "Frecuencia no permitida."
        else -> null
    }
}
