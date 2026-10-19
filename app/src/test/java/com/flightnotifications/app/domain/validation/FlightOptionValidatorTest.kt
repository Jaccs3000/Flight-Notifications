package com.flightnotifications.app.domain.validation

import com.flightnotifications.app.data.remote.SerpApiJsonParser
import com.flightnotifications.app.domain.model.SearchCriteria
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FlightOptionValidatorTest {
    private val parser = SerpApiJsonParser()
    private val criteria = SearchCriteria(LocalDate.parse("2027-03-25"), LocalDate.parse("2027-03-28"), true)

    @Test
    fun directOutboundMatchesExpectedAirportsAndDate() {
        val response = fixture("outbound.json")
        val option = parser.parse(response).options.first()

        assertTrue(FlightOptionValidator.isOutbound(option, criteria))
    }

    @Test
    fun detectsReturnOnSelectedDateAndAfterOutbound() {
        val outbound = parser.parse(fixture("outbound.json")).options.first()
        val returning = parser.parse(fixture("return.json")).options.first()

        assertTrue(FlightOptionValidator.isReturn(returning, criteria))
        assertTrue(FlightOptionValidator.returnAfterOutbound(outbound, returning))
    }

    @Test
    fun rejectsWrongDateOrConnectionWhenDirectOnly() {
        val outbound = parser.parse(fixture("outbound.json")).options.first()
        val wrongDate = outbound.copy(
            segments = outbound.segments.map { segment ->
                segment.copy(departure = segment.departure.copy(localDateTime = segment.departure.localDateTime!!.plusDays(1)))
            },
        )
        val connected = outbound.copy(segments = listOf(outbound.segments.first(), outbound.segments.first()))

        assertFalse(FlightOptionValidator.isOutbound(wrongDate, criteria))
        assertFalse(FlightOptionValidator.isOutbound(connected, criteria))
    }

    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(javaClass.getResource("/fixtures/$name")!!.readText()) as JsonObject
}
