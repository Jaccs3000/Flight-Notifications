package com.flightnotifications.app.domain.model

import java.time.LocalDate

data class FlightDatePair(val outbound: LocalDate, val returning: LocalDate?)

/** Builds the valid exact-date searches covered by an alert's selected date windows. */
object FlexibleFlightDates {
    const val MAX_DATE_PAIRS = 49

    fun pairs(
        outboundStart: LocalDate,
        outboundEnd: LocalDate,
        returnStart: LocalDate?,
        returnEnd: LocalDate?,
        todayAtOrigin: LocalDate,
    ): List<FlightDatePair> {
        if (outboundEnd.isBefore(outboundStart)) return emptyList()
        val outboundDates = days(outboundStart, outboundEnd).filterNot { it.isBefore(todayAtOrigin) }
        if (returnStart == null) return outboundDates.map { FlightDatePair(it, null) }
        val actualReturnEnd = returnEnd ?: returnStart
        if (actualReturnEnd.isBefore(returnStart)) return emptyList()
        val returnDates = days(returnStart, actualReturnEnd)
        return outboundDates.flatMap { outbound ->
            returnDates.filter { it.isAfter(outbound) }.map { returning -> FlightDatePair(outbound, returning) }
        }
    }

    fun allPairs(
        outboundStart: LocalDate,
        outboundEnd: LocalDate,
        returnStart: LocalDate?,
        returnEnd: LocalDate?,
    ): List<FlightDatePair> = pairs(outboundStart, outboundEnd, returnStart, returnEnd, outboundStart)

    private fun days(start: LocalDate, end: LocalDate): List<LocalDate> {
        val count = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
        if (count !in 1..MAX_DATE_PAIRS.toLong()) return emptyList()
        return (0 until count.toInt()).map { offset -> start.plusDays(offset.toLong()) }
    }
}
