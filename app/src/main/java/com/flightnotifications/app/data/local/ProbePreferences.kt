package com.flightnotifications.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.probeDataStore by preferencesDataStore(name = "probe_settings")

data class ProbePreferences(
    val outboundDate: LocalDate = LocalDate.of(2027, 1, 25),
    val returnDate: LocalDate = LocalDate.of(2027, 2, 3),
    val directOnly: Boolean = true,
    val localBudgetLimit: Int = 200,
    val alertOrder: List<String> = emptyList(),
)

class ProbePreferencesRepository(private val context: Context) {
    val preferences: Flow<ProbePreferences> = context.probeDataStore.data.map { stored ->
        ProbePreferences(
            outboundDate = stored[OUTBOUND]?.let(::parseDate) ?: LocalDate.of(2027, 1, 25),
            returnDate = stored[RETURN]?.let(::parseDate) ?: LocalDate.of(2027, 2, 3),
            directOnly = stored[DIRECT_ONLY] ?: true,
            localBudgetLimit = (stored[LOCAL_BUDGET_LIMIT] ?: 200).coerceIn(1, 200),
            alertOrder = stored[ALERT_ORDER]?.let { raw ->
                runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
            }.orEmpty(),
        )
    }

    suspend fun update(value: ProbePreferences) {
        context.probeDataStore.edit { stored ->
            stored[OUTBOUND] = value.outboundDate.toString()
            stored[RETURN] = value.returnDate.toString()
            stored[DIRECT_ONLY] = value.directOnly
            stored[LOCAL_BUDGET_LIMIT] = value.localBudgetLimit.coerceIn(1, 200)
        }
    }

    suspend fun updateAlertOrder(order: List<String>) {
        context.probeDataStore.edit { stored ->
            stored[ALERT_ORDER] = Json.encodeToString(order.distinct())
        }
    }

    /** Applies the user's requested comparison dates once, including on app upgrades with older saved dates. */
    suspend fun applyRequestedComparisonDatesOnce() {
        context.probeDataStore.edit { stored ->
            if ((stored[DATE_PRESET_VERSION] ?: 0) < REQUESTED_DATE_PRESET_VERSION) {
                stored[OUTBOUND] = REQUESTED_OUTBOUND_DATE.toString()
                stored[RETURN] = REQUESTED_RETURN_DATE.toString()
                stored[DATE_PRESET_VERSION] = REQUESTED_DATE_PRESET_VERSION
            }
        }
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private companion object {
        val OUTBOUND = stringPreferencesKey("outbound_date")
        val RETURN = stringPreferencesKey("return_date")
        val DIRECT_ONLY = booleanPreferencesKey("direct_only")
        val LOCAL_BUDGET_LIMIT = intPreferencesKey("local_flight_get_limit")
        val ALERT_ORDER = stringPreferencesKey("alert_order")
        val DATE_PRESET_VERSION = intPreferencesKey("comparison_date_preset_version")
        const val REQUESTED_DATE_PRESET_VERSION = 1
        val REQUESTED_OUTBOUND_DATE: LocalDate = LocalDate.of(2027, 1, 25)
        val REQUESTED_RETURN_DATE: LocalDate = LocalDate.of(2027, 2, 3)
    }
}
