package com.flightnotifications.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.probeDataStore by preferencesDataStore(name = "probe_settings")

data class ProbePreferences(
    val outboundDate: LocalDate = LocalDate.of(2027, 3, 25),
    val returnDate: LocalDate = LocalDate.of(2027, 3, 28),
    val directOnly: Boolean = true,
)

class ProbePreferencesRepository(private val context: Context) {
    val preferences: Flow<ProbePreferences> = context.probeDataStore.data.map { stored ->
        ProbePreferences(
            outboundDate = stored[OUTBOUND]?.let(::parseDate) ?: LocalDate.of(2027, 3, 25),
            returnDate = stored[RETURN]?.let(::parseDate) ?: LocalDate.of(2027, 3, 28),
            directOnly = stored[DIRECT_ONLY] ?: true,
        )
    }

    suspend fun update(value: ProbePreferences) {
        context.probeDataStore.edit { stored ->
            stored[OUTBOUND] = value.outboundDate.toString()
            stored[RETURN] = value.returnDate.toString()
            stored[DIRECT_ONLY] = value.directOnly
        }
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private companion object {
        val OUTBOUND = stringPreferencesKey("outbound_date")
        val RETURN = stringPreferencesKey("return_date")
        val DIRECT_ONLY = booleanPreferencesKey("direct_only")
    }
}
