package com.flightnotifications.app.domain.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats a locally observed airport departure time without applying a device time-zone conversion. */
object FlightDepartureTime {
    private val displayFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun format(value: String?): String? {
        val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val normalized = if ('T' !in text && ' ' in text) text.replaceFirst(' ', 'T') else text
        return runCatching { LocalDateTime.parse(normalized).format(displayFormatter) }.getOrNull()
    }
}
