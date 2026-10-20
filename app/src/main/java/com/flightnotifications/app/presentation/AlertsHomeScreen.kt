package com.flightnotifications.app.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flightnotifications.app.R
import com.flightnotifications.app.data.repository.AlertWithResult
import com.flightnotifications.app.domain.validation.ColombianAirportCatalog
import com.flightnotifications.app.ui.theme.FlightColors
import java.time.LocalDate

@Composable
internal fun AlertsHomeScreen(
    state: FlightAlertsUiState,
    airportCatalog: ColombianAirportCatalog,
    notificationsAllowed: Boolean,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("Todas") }
    val today = LocalDate.now(java.time.ZoneId.of("America/Bogota"))
    val activeCount = state.alerts.count { it.alert.enabled && !isPastOutbound(it.alert) }
    val shown = state.alerts.filter { item ->
        val active = item.alert.enabled && !isPastOutbound(item.alert)
        when (filter) {
            "Activas" -> active
            "Pausadas" -> !active
            else -> true
        }
    }

    AppPage {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Mis alertas", style = MaterialTheme.typography.displaySmall)
            Text("$activeCount activas · ${state.alerts.size} guardadas", style = MaterialTheme.typography.bodyLarge, color = FlightColors.SecondaryText)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Todas", "Activas", "Pausadas").forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { filter = item },
                    label = { Text(item) },
                )
            }
        }

        if (shown.isEmpty()) {
            EmptyState(
                title = if (state.alerts.isEmpty()) "Crea tu primera alerta" else "No hay alertas en este filtro",
                detail = if (state.alerts.isEmpty()) "Elige la ruta, las fechas y tu precio máximo. La alerta se guardará pausada." else "Prueba otro filtro para ver tus alertas.",
                action = if (state.alerts.isEmpty()) ({ Button(onClick = onNew, modifier = Modifier.testTag("new-alert")) { Text("Nueva alerta") } }) else null,
            )
        } else {
            shown.forEach { item ->
                AlertHomeCard(
                    item = item,
                    airportCatalog = airportCatalog,
                    today = today,
                    running = state.runningAlertId == item.alert.id,
                    onOpen = { onOpen(item.alert.id) },
                    onToggle = { onToggle(item.alert.id, it) },
                )
            }
        }

        if (activeCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VectorGlyph(R.drawable.ic_schedule, tint = FlightColors.SecondaryText)
                Text("Android puede aplazar las revisiones por ahorro de batería o falta de red.", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
            }
            if (!notificationsAllowed) {
                AppCard {
                    Text("Notificaciones desactivadas", style = MaterialTheme.typography.titleMedium)
                    Text("Las revisiones automáticas se pausan mientras Android no pueda mostrar avisos.", color = FlightColors.SecondaryText)
                    OutlinedButton(onClick = onOpenNotificationSettings) { Text("Abrir ajustes de notificaciones") }
                }
            }
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FlightColors.Primary) }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

        if (state.alerts.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onNew, modifier = Modifier.testTag("new-alert")) {
                    Text("＋   Nueva alerta", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun AlertHomeCard(
    item: AlertWithResult,
    airportCatalog: ColombianAirportCatalog,
    today: LocalDate,
    running: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val alert = item.alert
    val result = item.result
    val expired = runCatching { LocalDate.parse(alert.outboundDate).isBefore(today) }.getOrDefault(true)
    val active = alert.enabled && !expired
    val current = result?.let { resultIsCurrent(alert, it) } == true
    val best = result?.lastVerifiedTotalCop?.takeIf { current }
    val bestOffer = storedOffers(result).minByOrNull { it.totalCop }
    val cityPair = "${cityName(alert.originIata, airportCatalog)} ↔ ${cityName(alert.destinationIata, airportCatalog)}"
    val displayTitle = if (alert.name.startsWith(cityPair, ignoreCase = true)) cityPair else alert.name

    AppCard(contentPadding = 18.dp, modifier = Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("$cityPair · ${alert.originIata} ↔ ${alert.destinationIata} · ${formatDateRange(alert.outboundDate, alert.returnDate)}", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppSwitch(checked = active, onCheckedChange = onToggle, enabled = !running && !expired, modifier = Modifier.testTag("toggle-alert-${alert.id}"))
                Text(if (expired) "Vencida" else if (active) "Activa" else "Pausada", style = MaterialTheme.typography.labelMedium, color = if (active) FlightColors.Primary else FlightColors.SecondaryText)
            }
        }

        if (best != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(formatCop(best), style = MaterialTheme.typography.displaySmall, color = FlightColors.Primary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill("Verificado", positive = true)
            }
            Text("ida y vuelta · 1 adulto", style = MaterialTheme.typography.bodyLarge, color = FlightColors.SecondaryText)
            Text("✈  ${bestOffer?.outboundAirline ?: "Aerolínea"} · ${if (alert.directOnly) "Directos" else "Escalas permitidas"}", style = MaterialTheme.typography.bodyLarge, color = FlightColors.SecondaryText)
        } else if (result?.lastVerifiedTotalCop != null) {
            Text("Precio anterior ${formatCop(result.lastVerifiedTotalCop)}", style = MaterialTheme.typography.titleMedium, color = FlightColors.SecondaryText)
            Text("La última revisión no confirmó esta tarifa con los criterios actuales.", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
        } else {
            Text(
                when {
                    result == null -> "Todavía no hay resultados."
                    result.status == "SIN_RESULTADOS_DEVUELTOS" -> "SerpApi no devolvió opciones en la última consulta."
                    result.status == "EXPIRED" || expired -> "La fecha de ida ya pasó."
                    else -> statusDescription(result.status)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = FlightColors.SecondaryText,
            )
        }

        SmallDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Tu máximo", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
                Text(formatCop(alert.maxTotalCop), style = MaterialTheme.typography.titleLarge)
            }
            androidx.compose.material3.VerticalDivider(modifier = Modifier.height(42.dp), color = FlightColors.Outline)
            Column(Modifier.weight(1f)) {
                Text("Revisión automática", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
                Text("Cada ${alert.frequencyHours} h", style = MaterialTheme.typography.titleLarge)
            }
        }

        SmallDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                result?.lastAttemptAt?.let { "Última revisión: ${formatLocalTime(it)}" } ?: "Sin revisiones todavía",
                style = MaterialTheme.typography.bodyMedium,
                color = FlightColors.SecondaryText,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ver resultados", color = FlightColors.Primary, style = MaterialTheme.typography.labelLarge)
                VectorGlyph(R.drawable.ic_chevron_right, tint = FlightColors.Primary)
            }
        }

    }
}

private fun statusDescription(status: String): String = when (status) {
    "NO_VERIFICADO" -> "Se encontraron vuelos, pero no se verificó un total de ida y vuelta."
    "ERROR" -> "La última consulta terminó con un error."
    "CRITERIA_CHANGED", "POLICY_CHANGED" -> "Cambiaron los criterios; hace falta una revisión nueva."
    else -> "Aún no hay una tarifa verificada con estos criterios."
}
