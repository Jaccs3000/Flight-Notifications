package com.flightnotifications.app.domain.validation

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale
import java.time.ZoneId

@Serializable
data class AirportLocation(
    val iata: String,
    val name: String,
    val city: String,
    val country: String,
    val timeZone: String,
    val state: String = "",
)

@Serializable
private data class AirportCatalogDocument(
    val generatedAt: String,
    val sources: List<String>,
    val airports: List<AirportLocation>,
)

/** Worldwide offline airport index. Every included airport has a usable IATA code. */
class AirportCatalog(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val document = json.decodeFromString<AirportCatalogDocument>(
        context.assets.open("world_airports.json").bufferedReader(Charsets.UTF_8).use { it.readText() },
    )
    private val validAirports = document.airports
        .filter {
            it.iata.matches(Regex("[A-Z]{3}")) && it.name.isNotBlank() && it.city.isNotBlank() &&
                it.country.matches(Regex("[A-Z]{2}")) && runCatching { ZoneId.of(it.timeZone) }.isSuccess
        }
        .distinctBy { it.iata }
    private val byCode = validAirports.associateBy { it.iata }
    private val zonesByCode = validAirports.associate { it.iata to ZoneId.of(it.timeZone) }
    private val searchIndex = validAirports.map { airport ->
        val countryName = Locale.Builder().setRegion(airport.country).build()
            .getDisplayCountry(Locale.forLanguageTag("es-CO")).ifBlank { airport.country }
        AirportSearchEntry(
            airport = airport,
            code = normalizeAirportText(airport.iata),
            city = normalizeAirportText(airport.city),
            name = normalizeAirportText(airport.name),
            searchable = listOf(airport.iata, airport.city, airport.name, airport.state, airport.country, countryName)
                .filter(String::isNotBlank)
                .map(::normalizeAirportText),
        )
    }

    val airports: List<AirportLocation> = validAirports
    val generatedAt: String = document.generatedAt
    val sources: List<String> = document.sources

    fun find(code: String): AirportLocation? = byCode[code.trim().uppercase()]
    fun containsAirport(code: String): Boolean = find(code) != null
    fun timeZoneFor(code: String): ZoneId? = zonesByCode[code.trim().uppercase()]

    fun suggestions(query: String, limit: Int = 7): List<AirportLocation> {
        val normalized = normalizeAirportText(query)
        if (normalized.isBlank()) {
            return listOf("BOG", "MDE", "CTG", "CLO", "MIA", "JFK", "MAD", "LHR")
                .mapNotNull(::find)
                .take(limit)
        }
        val comparator = compareBy<AirportSearchEntry> {
            when {
                it.code == normalized -> 0
                it.city.startsWith(normalized) -> 1
                it.name.startsWith(normalized) -> 2
                else -> 3
            }
        }.thenBy { it.city }.thenBy { it.airport.country }.thenBy { it.airport.iata }
        val best = ArrayList<AirportSearchEntry>(limit)
        searchIndex.forEach { entry ->
            if (entry.searchable.none { it.contains(normalized) }) return@forEach
            val insertion = best.binarySearch(entry, comparator).let { if (it < 0) -it - 1 else it }
            if (insertion < limit) {
                best.add(insertion, entry)
                if (best.size > limit) best.removeAt(limit)
            }
        }
        return best.map { it.airport }
    }

    private data class AirportSearchEntry(
        val airport: AirportLocation,
        val code: String,
        val city: String,
        val name: String,
        val searchable: List<String>,
    )
}

private fun normalizeAirportText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase(Locale.ROOT)
    .trim()

@Serializable
data class ColombianAirline(val name: String, val code: String)

@Serializable
private data class AirlineCatalogDocument(
    val reviewedAt: String,
    val source: String,
    val airlines: List<ColombianAirline>,
)

class ColombianAirlineCatalog(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val document = json.decodeFromString<AirlineCatalogDocument>(
        context.assets.open("colombian_airlines.json").bufferedReader(Charsets.UTF_8).use { it.readText() },
    )
    val airlines: List<ColombianAirline> = document.airlines.sortedBy { it.name }
    val reviewedAt: String = document.reviewedAt
    val source: String = document.source
}
