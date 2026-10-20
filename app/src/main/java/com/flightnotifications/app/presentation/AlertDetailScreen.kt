package com.flightnotifications.app.presentation

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flightnotifications.app.R
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.local.SentNotificationEntity
import com.flightnotifications.app.data.repository.AlertWithResult
import com.flightnotifications.app.domain.validation.ColombianAirportCatalog
import com.flightnotifications.app.ui.theme.FlightColors

@Composable
internal fun AlertDetailScreen(
    alertId: String?,
    notification: SentNotificationEntity?,
    state: FlightAlertsUiState,
    airportCatalog: ColombianAirportCatalog,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSearch: (String) -> Unit,
) {
    val item = alertId?.let { id -> state.alerts.firstOrNull { it.alert.id == id } }
    var menuExpanded by rememberSaveable(alertId) { mutableStateOf(false) }
    var allOffersExpanded by rememberSaveable(alertId) { mutableStateOf(false) }
    val context = LocalContext.current

    AppPage {
        if (item == null) {
            StandardBack("Alerta", onBack)
            EmptyState("Alerta no disponible", "Puede que se haya eliminado. Vuelve a Mis alertas para elegir otra.")
            return@AppPage
        }
        val alert = item.alert
        val result = item.result
        val current = result?.let { resultIsCurrent(alert, it) } == true
        val offers = storedOffers(result).sortedBy { it.totalCop }
        val bestOffer = offers.firstOrNull()
        val total = result?.lastVerifiedTotalCop?.takeIf { current } ?: bestOffer?.totalCop?.takeIf { current }

        StandardBack(
            title = "${cityName(alert.originIata, airportCatalog)} ↔ ${cityName(alert.destinationIata, airportCatalog)}",
            onBack = onBack,
            subtitle = "${formatDateRange(alert.outboundDate, alert.returnDate)} · 1 adulto · Económica",
            trailing = {
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp).testTag("alert-actions")) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "Acciones", tint = FlightColors.OnSurface)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Editar alerta") }, onClick = { menuExpanded = false; onEdit(alert.id) }, modifier = Modifier.testTag("edit-alert-${alert.id}"))
                        DropdownMenuItem(text = { Text("Eliminar alerta") }, onClick = { menuExpanded = false; onDelete(alert.id) }, modifier = Modifier.testTag("delete-alert-${alert.id}"))
                    }
                }
            },
        )

        notification?.let { notice ->
            AppCard {
                Text("Aviso guardado", style = MaterialTheme.typography.titleMedium)
                Text("${notice.routeSummary} · ${formatCop(notice.priceCop)} ida y vuelta", style = MaterialTheme.typography.bodyLarge)
                Text("${notice.outboundCarrier} / ${notice.returnCarrier} · enviado ${formatLocalFull(notice.sentAt)}", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
                Text("Este es el importe de ese aviso. El resultado actual de la alerta se muestra por separado.", style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
            }
        }

        if (result == null) {
            EmptyState("Aún no hay resultados", "La alerta está configurada, pero todavía no tiene una revisión guardada.")
        } else {
            AppCard {
                Text(if (current) "Tarifa más baja observada" else "Estado de la última revisión", style = MaterialTheme.typography.titleMedium, color = FlightColors.SecondaryText)
                if (total != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(formatCop(total), style = MaterialTheme.typography.displaySmall, color = FlightColors.Primary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusPill("Verificado", positive = true)
                    }
                    Text("COP · ida y vuelta", style = MaterialTheme.typography.bodyLarge, color = FlightColors.SecondaryText)
                    Text(
                        if (total <= alert.maxTotalCop) "Dentro de tu máximo de ${formatCop(alert.maxTotalCop)}" else "Supera tu máximo de ${formatCop(alert.maxTotalCop)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (total <= alert.maxTotalCop) FlightColors.Success else MaterialTheme.colorScheme.error,
                    )
                    SmallDivider()
                    bestOffer?.let { offer ->
                        OfferDirection("Ida", alert.originIata, alert.destinationIata, offer.outboundAirline, offer.outbound.size - 1)
                        SmallDivider()
                        OfferDirection("Regreso", alert.destinationIata, alert.originIata, offer.returnAirline, offer.returning.size - 1)
                    }
                    SmallDivider()
                    Text("Consultado ${result.networkReceivedAt?.let(::formatLocalTime) ?: formatLocalTime(result.lastAttemptAt)}", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
                } else {
                    StatusPill(statusLabel(result.status), positive = false)
                    Text(statusDetail(result.status, result.lastError), style = MaterialTheme.typography.bodyLarge)
                    result.lastVerifiedTotalCop?.let { previous ->
                        SmallDivider()
                        Text("Último precio verificado anteriormente", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
                        Text(formatCop(previous), style = MaterialTheme.typography.headlineMedium, color = FlightColors.SecondaryText)
                        Text("Criterios: ${result.verifiedCriteria?.let(::formatCriteriaLabel) ?: "no disponibles"} · ${result.lastVerifiedAt?.let(::formatLocalFull) ?: "fecha no disponible"}.", style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
                    }
                }
            }

            if (current && result.explorationLimited) {
                AppCard(contentPadding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VectorGlyph(R.drawable.ic_info, tint = FlightColors.Primary)
                        Column {
                            Text("Exploración parcial", style = MaterialTheme.typography.titleMedium)
                            Text("No se comprobaron todas las combinaciones.", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
                        }
                    }
                }
            }

            if (current && offers.isNotEmpty()) {
                val grouped = offers.groupBy { "${it.outboundAirline} / ${it.returnAirline}" }
                    .mapValues { (_, values) -> values.minOf { it.totalCop } }
                    .entries.sortedBy { it.value }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Otras tarifas verificadas", style = MaterialTheme.typography.headlineSmall)
                    AppCard {
                        grouped.filterIndexed { index, _ -> index != 0 }.take(3).forEachIndexed { index, entry ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(entry.key, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(formatCop(entry.value), style = MaterialTheme.typography.titleMedium, color = FlightColors.Primary)
                            }
                            if (index != grouped.filterIndexed { i, _ -> i != 0 }.take(3).lastIndex) SmallDivider()
                        }
                        if (grouped.size > 1) {
                            SmallDivider()
                            TextButton(onClick = { allOffersExpanded = !allOffersExpanded }, modifier = Modifier.fillMaxWidth().testTag("show-all-offers")) {
                                Text(if (allOffersExpanded) "Ocultar combinaciones" else "Ver las ${offers.size} combinaciones")
                            }
                        }
                    }
                    if (allOffersExpanded) {
                        offers.forEachIndexed { index, offer ->
                            OfferCard(offer, index + 1, airportCatalog)
                        }
                    }
                }
            }

            result.airlinesJson?.let { raw ->
                val names = runCatching { kotlinx.serialization.json.Json.decodeFromString<List<String>>(raw) }.getOrNull().orEmpty()
                if (names.isNotEmpty()) Text("Aerolíneas encontradas: ${names.distinct().joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
            }

            result.googleFlightsUrl?.takeIf(::isSafeGoogleFlightsUrl)?.let { url ->
                Button(
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                ) {
                    VectorGlyph(R.drawable.ic_open_in_new, tint = androidx.compose.ui.graphics.Color.White)
                    Text("Abrir Google Flights", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                }
                Text("Precio observado. Verifica condiciones y disponibilidad antes de comprar.", style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText, modifier = Modifier.fillMaxWidth())
            }

            OutlinedButton(
                onClick = { onSearch(alert.id) },
                enabled = state.runningAlertId != alert.id,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("review-alert-now"),
            ) {
                Text(if (state.runningAlertId == alert.id) "Revisando…" else "Revisar ahora", style = MaterialTheme.typography.titleMedium)
            }
            Text("Consume hasta 4 consultas de vuelos.", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText, modifier = Modifier.fillMaxWidth())
            state.message?.let { Text(it, color = FlightColors.Primary, style = MaterialTheme.typography.bodyMedium) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun OfferDirection(label: String, from: String, to: String, carrier: String, stops: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        VectorGlyph(R.drawable.ic_flight, tint = FlightColors.SecondaryText, modifier = Modifier.size(30.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium)
        Text("$carrier · ${if (stops <= 0) "Directo" else "$stops escala(s)"}", style = MaterialTheme.typography.bodyLarge, color = FlightColors.SecondaryText)
        }
    }
}

@Composable
private fun OfferCard(offer: com.flightnotifications.app.domain.model.VerifiedRoundTripOffer, number: Int, airportCatalog: ColombianAirportCatalog) {
    AppCard(contentPadding = 16.dp) {
        Text("Combinación $number", style = MaterialTheme.typography.titleMedium)
        Text("Ida: ${cityName(offer.outbound.firstOrNull()?.fromIata.orEmpty(), airportCatalog)} → ${cityName(offer.outbound.lastOrNull()?.toIata.orEmpty(), airportCatalog)} · ${offer.outboundAirline} · ${formatStops(offer.outbound.size - 1)}", style = MaterialTheme.typography.bodyMedium)
        Text("Regreso: ${cityName(offer.returning.firstOrNull()?.fromIata.orEmpty(), airportCatalog)} → ${cityName(offer.returning.lastOrNull()?.toIata.orEmpty(), airportCatalog)} · ${offer.returnAirline} · ${formatStops(offer.returning.size - 1)}", style = MaterialTheme.typography.bodyMedium)
        Text("Total ida y vuelta: ${formatCop(offer.totalCop)}", style = MaterialTheme.typography.titleMedium, color = FlightColors.Primary)
    }
}

private fun statusLabel(status: String): String = when (status) {
    "VERIFICADO" -> "Verificado"
    "NO_VERIFICADO" -> "No verificado"
    "SIN_RESULTADOS_DEVUELTOS" -> "Sin opciones devueltas"
    "ERROR" -> "Error de consulta"
    "EXPIRED" -> "Fechas vencidas"
    else -> "Resultado anterior"
}

private fun statusDetail(status: String, lastError: String?): String = when (status) {
    "NO_VERIFICADO" -> "La respuesta incluyó itinerarios, pero no permitió confirmar un total de ida y vuelta. No se informa una tarifa como actual."
    "SIN_RESULTADOS_DEVUELTOS" -> "SerpApi no devolvió opciones para esta búsqueda. Esto no confirma que no existan vuelos."
    "ERROR" -> "La última revisión terminó con un error. ${lastError.orEmpty()} Revisa el diagnóstico antes de volver a consultar."
    "EXPIRED" -> "La fecha de ida ya venció. Edita la alerta antes de activarla."
    "CRITERIA_CHANGED", "POLICY_CHANGED" -> "Los criterios cambiaron desde la última tarifa verificada. Hace falta una revisión nueva."
    else -> "Todavía no existe una tarifa verificada para mostrar."
}

private fun formatCriteriaLabel(criteria: String): String = criteria.replace("|", " · ").replace("direct=true", "directos").replace("direct=false", "escalas permitidas").replace('-', '→')

private fun isSafeGoogleFlightsUrl(value: String): Boolean = runCatching {
    val uri = value.toUri()
    uri.scheme == "https" && uri.host?.endsWith("google.com", ignoreCase = true) == true && uri.path?.contains("travel/flights") == true
}.getOrDefault(false)
