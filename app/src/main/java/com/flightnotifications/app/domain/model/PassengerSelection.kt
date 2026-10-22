package com.flightnotifications.app.domain.model

import kotlinx.serialization.Serializable

/** The exact age is kept locally; Google Flights accepts passenger category counts only. */
@Serializable
data class MinorPassenger(
    val age: Int,
    /** Required for ages 0–1: "lap" or "seat". */
    val infantSeating: String? = null,
)

data class PassengerCounts(
    val adults: Int,
    val children: Int,
    val infantsInSeat: Int,
    val infantsOnLap: Int,
) {
    val total: Int get() = adults + children + infantsInSeat + infantsOnLap
}

object PassengerSelection {
    const val MAX_PASSENGERS = 9

    fun counts(adults18Plus: Int, minors: List<MinorPassenger>): PassengerCounts = PassengerCounts(
        adults = adults18Plus + minors.count { it.age in 12..17 },
        children = minors.count { it.age in 2..11 },
        infantsInSeat = minors.count { it.age in 0..1 && it.infantSeating == "seat" },
        infantsOnLap = minors.count { it.age in 0..1 && it.infantSeating == "lap" },
    )

    fun validationError(adults18Plus: Int, minors: List<MinorPassenger>): String? {
        if (adults18Plus !in 1..MAX_PASSENGERS) return "Debe haber entre 1 y 9 adultos de 18 años o más."
        if (adults18Plus + minors.size > MAX_PASSENGERS) return "Google Flights permite consultar hasta 9 pasajeros por búsqueda."
        if (minors.any { it.age !in 0..17 }) return "La edad de cada menor debe estar entre 0 y 17 años."
        if (minors.any { it.age <= 1 && it.infantSeating !in setOf("lap", "seat") }) {
            return "Indica si cada bebé menor de 2 años viajará en brazos o en su propio asiento."
        }
        if (minors.any { it.age >= 2 && it.infantSeating != null }) return "La opción de asiento de bebé solo aplica a menores de 2 años."
        if (minors.count { it.age <= 1 && it.infantSeating == "lap" } > adults18Plus) {
            return "Debe viajar un adulto responsable por cada bebé que vaya en brazos."
        }
        return null
    }

    fun summary(adults18Plus: Int, minors: List<MinorPassenger>): String {
        val parts = buildList {
            add("$adults18Plus ${if (adults18Plus == 1) "adulto" else "adultos"}")
            minors.count { it.age in 12..17 }.takeIf { it > 0 }?.let { add("$it ${if (it == 1) "adolescente" else "adolescentes"}") }
            minors.count { it.age in 2..11 }.takeIf { it > 0 }?.let { add("$it ${if (it == 1) "niño" else "niños"}") }
            minors.count { it.age <= 1 }.takeIf { it > 0 }?.let { add("$it ${if (it == 1) "bebé" else "bebés"}") }
        }
        return parts.joinToString(" · ")
    }
}
