package com.flightnotifications.app.domain.validation

import com.flightnotifications.app.domain.model.FlightOption
import com.flightnotifications.app.domain.model.SearchCriteria
import java.time.LocalDate
import java.time.ZoneId

/** Validación conservadora para itinerarios domésticos de esta prueba CTG–CLO. */
object FlightOptionValidator {
    private val colombia = ZoneId.of("America/Bogota")

    fun isOutbound(option: FlightOption, criteria: SearchCriteria): Boolean =
        isRoute(option, criteria.origin, criteria.destination, criteria.outboundDate, criteria.directOnly)

    fun isReturn(option: FlightOption, criteria: SearchCriteria): Boolean =
        criteria.returnDate?.let { isRoute(option, criteria.destination, criteria.origin, it, criteria.directOnly) } ?: false

    private fun isRoute(
        option: FlightOption,
        origin: String,
        destination: String,
        expectedDate: LocalDate,
        directOnly: Boolean,
    ): Boolean {
        val segments = option.segments
        if (segments.isEmpty()) return false
        if (directOnly && segments.size != 1) return false
        if (segments.first().departure.code != origin || segments.last().arrival.code != destination) return false
        if (segments.first().departure.localDateTime?.toLocalDate() != expectedDate) return false

        for (index in segments.indices) {
            val current = segments[index]
            val departure = current.departure.localDateTime ?: return false
            val arrival = current.arrival.localDateTime ?: return false
            if (!arrival.isAfter(departure)) return false
            if (index > 0) {
                val previous = segments[index - 1]
                if (previous.arrival.code != current.departure.code) return false
                val previousArrival = previous.arrival.localDateTime ?: return false
                if (departure.atZone(colombia).toInstant() < previousArrival.atZone(colombia).toInstant()) return false
            }
        }
        return true
    }

    fun returnAfterOutbound(outbound: FlightOption, returning: FlightOption): Boolean {
        val lastArrival = outbound.segments.lastOrNull()?.arrival?.localDateTime ?: return false
        val firstReturn = returning.segments.firstOrNull()?.departure?.localDateTime ?: return false
        return firstReturn.atZone(colombia).toInstant() > lastArrival.atZone(colombia).toInstant()
    }
}
