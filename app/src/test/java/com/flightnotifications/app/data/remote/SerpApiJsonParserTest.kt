package com.flightnotifications.app.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(javaClass.getResource("/fixtures/$name")!!.readText()) as JsonObject
}
