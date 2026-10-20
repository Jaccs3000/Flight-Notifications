package com.flightnotifications.app.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerpApiJsonParserTest {
    private val parser = SerpApiJsonParser()

    @Test
    fun parsesEveryAirlineFromBothListsIncludingWingoAndUnknownNames() {
        val parsed = parser.parse(fixture("outbound.json"))

        assertEquals(2, parsed.options.size)
        assertTrue(parsed.airlinesDetected.contains("Wingo"))
        assertTrue(parsed.airlinesDetected.contains("Aerolínea Desconocida"))
        assertEquals("opaque-wingo-token", parsed.options.first().departureToken)
        assertEquals("https://www.google.com/travel/flights?q=CTG+CLO", parsed.googleFlightsUrl)
    }

    @Test
    fun rejectsUnsafeOrNonGoogleLinkWithoutDiscardingFlights() {
        val raw = fixture("outbound.json").toMutableMap()
        val metadata = (raw["search_metadata"] as JsonObject).toMutableMap()
        metadata["google_flights_url"] = Json.parseToJsonElement("\"https://example.com/steal?api_key=x\"")
        raw["search_metadata"] = JsonObject(metadata)

        val parsed = parser.parse(JsonObject(raw))

        assertEquals(2, parsed.options.size)
        assertFalse(parsed.googleFlightsUrl != null)

        metadata["google_flights_url"] = Json.parseToJsonElement(
            "\"https://www.google.com/travel/flights?q=%2561pi_key%253Dhidden\"",
        )
        raw["search_metadata"] = JsonObject(metadata)

        val encodedSensitiveMarker = parser.parse(JsonObject(raw))

        assertFalse(encodedSensitiveMarker.googleFlightsUrl != null)
    }

    @Test
    fun malformedCandidatesDoNotHideValidCandidates() {
        val raw = fixture("outbound.json").toMutableMap()
        raw["other_flights"] = Json.parseToJsonElement("[{\"flights\":[{\"airline\":\"Aerolínea rota\"}],\"departure_token\":\"bad\"}]")

        val parsed = parser.parse(JsonObject(raw))

        assertEquals(1, parsed.options.size)
        assertEquals(1, parsed.malformedOptions)
        assertTrue(parsed.airlinesDetected.contains("Wingo"))
        assertTrue(parsed.airlinesDetected.contains("Aerolínea rota"))
    }

    @Test
    fun acceptsEmptyFlightListsButDoesNotTreatMissingOrWrongShapedListsAsNoResults() {
        val raw = fixture("outbound.json").toMutableMap()
        raw["best_flights"] = Json.parseToJsonElement("[]")
        raw["other_flights"] = Json.parseToJsonElement("[]")

        assertTrue(parser.parse(JsonObject(raw)).options.isEmpty())

        raw.remove("best_flights")
        raw.remove("other_flights")
        assertEquals("INVALID_RESPONSE", runCatching { parser.parse(JsonObject(raw)) }.exceptionOrNull()?.message)

        raw["best_flights"] = Json.parseToJsonElement("{}")
        raw["other_flights"] = Json.parseToJsonElement("[]")
        assertEquals("INVALID_RESPONSE", runCatching { parser.parse(JsonObject(raw)) }.exceptionOrNull()?.message)
    }

    @Test
    fun rejectsNotReadyAndServiceErrorResponsesWithDistinctCategories() {
        val pending = fixture("outbound.json").toMutableMap()
        val pendingMetadata = (pending["search_metadata"] as JsonObject).toMutableMap()
        pendingMetadata["status"] = Json.parseToJsonElement("\"Processing\"")
        pending["search_metadata"] = JsonObject(pendingMetadata)
        assertEquals("SEARCH_NOT_READY", runCatching { parser.parse(JsonObject(pending)) }.exceptionOrNull()?.message)

        val serviceError = fixture("outbound.json").toMutableMap()
        serviceError["error"] = Json.parseToJsonElement("\"Invalid API key\"")
        assertEquals("SERVICE_ERROR", runCatching { parser.parse(JsonObject(serviceError)) }.exceptionOrNull()?.message)
    }

    @Test
    fun nonPositiveFractionalAndOverflowPricesAreNeverParsedAsCopAmounts() {
        val raw = fixture("outbound.json").toMutableMap()
        raw["best_flights"] = Json.parseToJsonElement(
            """[
                {"flights":[{"departure_airport":{"id":"CTG"},"arrival_airport":{"id":"CLO"}}],"price":0},
                {"flights":[{"departure_airport":{"id":"CTG"},"arrival_airport":{"id":"CLO"}}],"price":-1},
                {"flights":[{"departure_airport":{"id":"CTG"},"arrival_airport":{"id":"CLO"}}],"price":1.5},
                {"flights":[{"departure_airport":{"id":"CTG"},"arrival_airport":{"id":"CLO"}}],"price":9223372036854775808}
            ]""",
        )
        raw["other_flights"] = Json.parseToJsonElement("[]")

        val parsed = parser.parse(JsonObject(raw))

        assertEquals(4, parsed.options.size)
        assertTrue(parsed.options.all { it.priceCop == null })
    }

    @Test
    fun mathematicallyIntegralDecimalPriceIsReadAsWholeCop() {
        val raw = fixture("outbound.json").toMutableMap()
        raw["best_flights"] = Json.parseToJsonElement(
            """[{"flights":[{"departure_airport":{"id":"CTG"},"arrival_airport":{"id":"CLO"}}],"price":320000.00}]""",
        )
        raw["other_flights"] = Json.parseToJsonElement("[]")

        assertEquals(320_000L, parser.parse(JsonObject(raw)).options.single().priceCop)
    }

    @Test
    fun malformedAirportAndInvalidLocalTimeAreHandledConservatively() {
        val raw = fixture("outbound.json").toMutableMap()
        raw["other_flights"] = Json.parseToJsonElement(
            """[
                {"flights":[{"departure_airport":{"id":"CTG","time":"not a date"},"arrival_airport":{"id":"CLO","time":"2027-03-25 11:00"}}],"price":300000},
                {"flights":[{"departure_airport":{"id":"CTGX"},"arrival_airport":{"id":"CLO"}}],"price":300000}
            ]""",
        )

        val parsed = parser.parse(JsonObject(raw))

        assertEquals(2, parsed.options.size) // the valid best-flight fixture remains available
        assertEquals(1, parsed.malformedOptions)
        assertNull(parsed.options.last().segments.first().departure.localDateTime)
    }

    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(javaClass.getResource("/fixtures/$name")!!.readText()) as JsonObject
}
