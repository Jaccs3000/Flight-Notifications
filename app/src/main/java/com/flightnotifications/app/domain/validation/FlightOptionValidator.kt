package com.flightnotifications.app.domain.validation

import com.flightnotifications.app.domain.model.FlightOption
import com.flightnotifications.app.domain.model.SearchCriteria
import java.time.LocalDate
import java.time.ZoneId

/** Checks route continuity and compares flight instants in each endpoint's own time zone. */
object FlightOptionValidator {
    fun isOutbound(
        option: FlightOption,
        criteria: SearchCriteria,
        timeZoneFor: (String) -> ZoneId? = { null },
    ): Boolean = isRoute(option, criteria.origin, criteria.destination, criteria.outboundDate, criteria.directOnly, timeZoneFor)

    fun isReturn(
        option: FlightOption,
        criteria: SearchCriteria,
        timeZoneFor: (String) -> ZoneId? = { null },
    ): Boolean = criteria.returnDate?.let {
        isRoute(option, criteria.destination, criteria.origin, it, criteria.directOnly, timeZoneFor)
    } ?: false

    private fun isRoute(
        option: FlightOption,
        origin: String,
        destination: String,
        expectedDate: LocalDate,
        directOnly: Boolean,
        timeZoneFor: (String) -> ZoneId?,
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
            val departureZone = timeZoneFor(current.departure.code)
            val arrivalZone = timeZoneFor(current.arrival.code)
            if (departureZone != null && arrivalZone != null) {
                if (!arrival.atZone(arrivalZone).toInstant().isAfter(departure.atZone(departureZone).toInstant())) return false
            } else if (current.durationMinutes != null && current.durationMinutes <= 0) {
                return false
            } else if (current.departure.code == current.arrival.code && !arrival.isAfter(departure)) {
                return false
            }
            if (index > 0) {
                val previous = segments[index - 1]
                if (previous.arrival.code != current.departure.code) return false
                val previousArrival = previous.arrival.localDateTime ?: return false
                // Both timestamps belong to the same connecting airport, so their local
                // wall-clock values can be compared directly without assuming Bogota time.
                if (departure < previousArrival) return false
            }
        }
        return true
    }

    fun returnAfterOutbound(outbound: FlightOption, returning: FlightOption): Boolean {
        val lastArrival = outbound.segments.lastOrNull()?.arrival?.localDateTime ?: return false
        val firstReturn = returning.segments.firstOrNull()?.departure?.localDateTime ?: return false
        val outboundArrivalAirport = outbound.segments.lastOrNull()?.arrival?.code ?: return false
        val returnDepartureAirport = returning.segments.firstOrNull()?.departure?.code ?: return false
        return outboundArrivalAirport == returnDepartureAirport && firstReturn.isAfter(lastArrival)
    }
}
