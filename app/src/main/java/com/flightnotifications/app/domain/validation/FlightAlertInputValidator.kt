package com.flightnotifications.app.domain.validation

import java.time.LocalDate
import com.flightnotifications.app.domain.model.MinorPassenger
import com.flightnotifications.app.domain.model.PassengerSelection

/** Pure validation for the saved-alert form; the caller supplies the local catalogue and COT date. */
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
        containsDomesticAirport: (String) -> Boolean,
        adults18Plus: Int = 1,
        minors: List<MinorPassenger> = emptyList(),
    ): String? = when {
        name.trim().length !in 3..80 -> "El nombre debe tener entre 3 y 80 caracteres."
        !containsDomesticAirport(origin) -> "El origen debe ser un aeropuerto colombiano del catálogo local."
        !containsDomesticAirport(destination) -> "El destino debe ser un aeropuerto colombiano del catálogo local."
        origin.trim().equals(destination.trim(), ignoreCase = true) -> "El origen y el destino deben ser distintos."
        outboundDate.isBefore(today) -> "La fecha de ida no puede estar en el pasado."
        returnDate != null && !returnDate.isAfter(outboundDate) -> "La fecha de regreso debe ser posterior a la ida."
        PassengerSelection.validationError(adults18Plus, minors) != null -> PassengerSelection.validationError(adults18Plus, minors)
        maximumCop <= 0L -> "El máximo total debe ser un valor positivo en COP."
        frequencyHours !in ALLOWED_FREQUENCIES -> "Frecuencia no permitida."
        else -> null
    }
}
