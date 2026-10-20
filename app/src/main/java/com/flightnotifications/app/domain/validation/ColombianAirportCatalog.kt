package com.flightnotifications.app.domain.validation

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.ZoneId

@Serializable
data class ColombianAirport(
    val iata: String,
    val name: String,
    val city: String,
    val department: String,
    val country: String = "CO",
    val timeZone: String = "America/Bogota",
)

@Serializable
private data class AirportCatalogDocument(
    val generatedAt: String,
    val sources: List<String>,
    val airports: List<ColombianAirport>,
)

class ColombianAirportCatalog(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val document = json.decodeFromString<AirportCatalogDocument>(
        context.assets.open("colombian_airports.json").bufferedReader(Charsets.UTF_8).use { it.readText() },
    )
    private val validAirports = document.airports
        .filter {
            it.iata.matches(Regex("[A-Z]{3}")) && it.country.equals("CO", ignoreCase = true) &&
                it.name.isNotBlank() && it.city.isNotBlank() && runCatching { ZoneId.of(it.timeZone) }.isSuccess
        }
        .distinctBy { it.iata }
    private val byCode = validAirports.associateBy { it.iata.uppercase() }

    val airports: List<ColombianAirport> = validAirports
    val reviewedAt: String = document.generatedAt
    val sources: List<String> = document.sources

    fun find(code: String): ColombianAirport? = byCode[code.trim().uppercase()]
    fun containsDomesticAirport(code: String): Boolean = find(code) != null
}

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
