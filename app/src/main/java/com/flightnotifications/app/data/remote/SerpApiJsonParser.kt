package com.flightnotifications.app.data.remote

import com.flightnotifications.app.domain.model.AirportPoint
import com.flightnotifications.app.domain.model.FlightOption
import com.flightnotifications.app.domain.model.FlightSegment
import com.flightnotifications.app.domain.model.ParsedFlights
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

class SerpApiJsonParser {
    fun parse(root: JsonObject): ParsedFlights {
        require(root["error"] == null) { "SERVICE_ERROR" }
        val metadata = root.objectAt("search_metadata") ?: throw IllegalArgumentException("INVALID_RESPONSE")
        val status = metadata.stringAt("status") ?: throw IllegalArgumentException("INVALID_RESPONSE")
        require(status.equals("Success", ignoreCase = true)) { "SEARCH_NOT_READY" }

        val malformed = intArrayOf(0)
        require(root["best_flights"] == null || root["best_flights"] is JsonArray) { "INVALID_RESPONSE" }
        require(root["other_flights"] == null || root["other_flights"] is JsonArray) { "INVALID_RESPONSE" }
        require(root["best_flights"] is JsonArray || root["other_flights"] is JsonArray) { "INVALID_RESPONSE" }
        val options = buildList {
            parseGroup(root["best_flights"], "best", malformed).forEach(::add)
            parseGroup(root["other_flights"], "other", malformed).forEach(::add)
        }
        val airlines = (airlinesIn(root["best_flights"]) + airlinesIn(root["other_flights"]))
            .toSortedSet(String.CASE_INSENSITIVE_ORDER)
        val safeUrl = metadata.stringAt("google_flights_url")?.takeIf(::isAllowedGoogleFlightsUrl)
        return ParsedFlights(options, airlines, status, safeUrl, malformed[0])
    }

    private fun parseGroup(element: JsonElement?, group: String, malformed: IntArray): List<FlightOption> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { optionElement ->
            try {
                parseOption(optionElement as? JsonObject ?: error("malformed"), group)
            } catch (_: Exception) {
                malformed[0]++
                null
            }
        }
    }

    private fun airlinesIn(element: JsonElement?): Set<String> {
        val options = element as? JsonArray ?: return emptySet()
        return options.flatMap { option ->
            val flights = (option as? JsonObject)?.get("flights") as? JsonArray ?: return@flatMap emptyList()
            flights.mapNotNull { flight ->
                (flight as? JsonObject)?.stringAt("airline")?.trim()?.takeIf(String::isNotEmpty)
            }.ifEmpty { listOf("Operador no informado") }
        }.toSet()
    }

    private fun parseOption(json: JsonObject, group: String): FlightOption {
        val flights = json["flights"] as? JsonArray ?: error("malformed")
        val segments = flights.map { element ->
            val flight = element as? JsonObject ?: error("malformed")
            val departure = flight.objectAt("departure_airport") ?: error("malformed")
            val arrival = flight.objectAt("arrival_airport") ?: error("malformed")
            FlightSegment(
                departure = airport(departure),
                arrival = airport(arrival),
                airline = flight.stringAt("airline"),
                flightNumber = flight.stringAt("flight_number"),
            )
        }
        if (segments.isEmpty()) error("malformed")
        return FlightOption(
            segments = segments,
            priceCop = json["price"].asIntegralLongOrNull(),
            type = json.stringAt("type"),
            departureToken = json.stringAt("departure_token")?.takeIf { it.isNotBlank() },
            sourceGroup = group,
        )
    }

    private fun airport(json: JsonObject): AirportPoint {
        val code = json.stringAt("id")?.uppercase()?.takeIf { it.length == 3 } ?: error("malformed")
        return AirportPoint(
            code = code,
            name = json.stringAt("name"),
            localDateTime = json.stringAt("time")?.let(::parseLocalDateTime),
        )
    }

    private fun parseLocalDateTime(value: String): LocalDateTime? = try {
        LocalDateTime.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT))
    } catch (_: Exception) {
        null
    }

    private fun isAllowedGoogleFlightsUrl(value: String): Boolean {
        return try {
            val uri = java.net.URI(value)
            if (uri.scheme != "https" || !uri.host.equals("www.google.com", ignoreCase = true) ||
                !uri.path.startsWith("/travel/flights")
            ) {
                false
            } else {
                var inspected = value
                var safe = true
                for (pass in 0 until MAX_URL_DECODE_PASSES) {
                    if (SENSITIVE_URL_MARKERS.any { marker -> inspected.contains(marker, ignoreCase = true) }) {
                        safe = false
                        break
                    }
                    if (pass < MAX_URL_DECODE_PASSES - 1) {
                        inspected = java.net.URLDecoder.decode(inspected, Charsets.UTF_8)
                    }
                }
                safe
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.stringAt(key: String): String? = (this[key] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
    private fun JsonElement?.asIntegralLongOrNull(): Long? = try {
        val primitive = this as? JsonPrimitive ?: return null
        val amount = BigDecimal(primitive.content)
        if (amount <= BigDecimal.ZERO || amount.stripTrailingZeros().scale() > 0) null else amount.longValueExact()
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MAX_URL_DECODE_PASSES = 3
        val SENSITIVE_URL_MARKERS = listOf("api_key", "departure_token", "booking_token")
    }
}
