package com.flightnotifications.app.domain.validation

import com.flightnotifications.app.data.remote.SerpApiJsonParser
import com.flightnotifications.app.domain.model.AirportPoint
import com.flightnotifications.app.domain.model.FlightOption
import com.flightnotifications.app.domain.model.FlightSegment
import com.flightnotifications.app.domain.model.SearchCriteria
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

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

    @Test
    fun validatesTheFullRouteEndpointsAndRequiresTimesForEverySegment() {
        val valid = option(
            segment("CTG", "2027-03-25T08:00", "CLO", "2027-03-25T09:30"),
        )
        val wrongOrigin = option(segment("BOG", "2027-03-25T08:00", "CLO", "2027-03-25T09:30"))
        val wrongDestination = option(segment("CTG", "2027-03-25T08:00", "BOG", "2027-03-25T09:30"))
        val missingTime = option(
            FlightSegment(AirportPoint("CTG", null, null), AirportPoint("CLO", null, null), "Carrier", "AB1"),
        )

        assertTrue(FlightOptionValidator.isOutbound(valid, criteria))
        assertFalse(FlightOptionValidator.isOutbound(wrongOrigin, criteria))
        assertFalse(FlightOptionValidator.isOutbound(wrongDestination, criteria))
        assertFalse(FlightOptionValidator.isOutbound(missingTime, criteria))
    }

    @Test
    fun permitsOnlyContinuousChronologicalConnectionsWhenDirectOnlyIsOff() {
        val allowConnections = criteria.copy(directOnly = false)
        val validConnection = option(
            segment("CTG", "2027-03-25T08:00", "BOG", "2027-03-25T09:00"),
            segment("BOG", "2027-03-25T10:00", "CLO", "2027-03-25T11:00"),
        )
        val disconnected = option(
            segment("CTG", "2027-03-25T08:00", "BOG", "2027-03-25T09:00"),
            segment("MDE", "2027-03-25T10:00", "CLO", "2027-03-25T11:00"),
        )
        val overlapping = option(
            segment("CTG", "2027-03-25T08:00", "BOG", "2027-03-25T10:30"),
            segment("BOG", "2027-03-25T10:00", "CLO", "2027-03-25T11:00"),
        )

        assertTrue(FlightOptionValidator.isOutbound(validConnection, allowConnections))
        assertFalse(FlightOptionValidator.isOutbound(validConnection, criteria))
        assertFalse(FlightOptionValidator.isOutbound(disconnected, allowConnections))
        assertFalse(FlightOptionValidator.isOutbound(overlapping, allowConnections))
    }

    @Test
    fun returnMustBeOnTheSelectedDateAndAfterTheOutboundArrival() {
        val outbound = option(segment("CTG", "2027-03-25T08:00", "CLO", "2027-03-25T09:30"))
        val laterReturn = option(segment("CLO", "2027-03-28T09:30", "CTG", "2027-03-28T11:00"))
        val equalInstantReturn = option(segment("CLO", "2027-03-25T09:30", "CTG", "2027-03-25T11:00"))
        val earlierReturn = option(segment("CLO", "2027-03-25T09:00", "CTG", "2027-03-25T10:00"))

        assertTrue(FlightOptionValidator.isReturn(laterReturn, criteria))
        assertTrue(FlightOptionValidator.returnAfterOutbound(outbound, laterReturn))
        assertFalse(FlightOptionValidator.returnAfterOutbound(outbound, equalInstantReturn))
        assertFalse(FlightOptionValidator.returnAfterOutbound(outbound, earlierReturn))
    }

    private fun option(vararg segments: FlightSegment) = FlightOption(
        segments = segments.toList(),
        priceCop = 350_000,
        type = "Round trip",
        departureToken = "test-token",
        sourceGroup = "test",
    )

    private fun segment(from: String, departure: String, to: String, arrival: String) = FlightSegment(
        departure = AirportPoint(from, null, LocalDateTime.parse(departure)),
        arrival = AirportPoint(to, null, LocalDateTime.parse(arrival)),
        airline = "Test Air",
        flightNumber = "TA100",
    )

    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(javaClass.getResource("/fixtures/$name")!!.readText()) as JsonObject
}
