package com.flightnotifications.app.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flightnotifications.app.domain.model.SearchCriteria
import com.flightnotifications.app.ui.theme.FlightColors
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun DiagnosticsScreen(
    probeState: ProbeUiState,
    alertsState: FlightAlertsUiState,
    schedulingStatus: String,
    onBack: () -> Unit,
    onCheckScheduling: () -> Unit,
    onOutboundDate: (LocalDate) -> Unit,
    onReturnDate: (LocalDate) -> Unit,
    onDirectOnly: (Boolean) -> Unit,
    onStartProbe: () -> Unit,
    onConfirmProbe: () -> Unit,
    onDismissProbe: () -> Unit,
    onCancelProbe: () -> Unit,
    onClearConnectionLog: () -> Unit,
    onClearFlightLog: () -> Unit,
) {
    var probeExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedLog by rememberSaveable { mutableStateOf("Conexión") }
    val context = LocalContext.current
    val activeAlerts = alertsState.alerts.filter { it.alert.enabled }
    val lastReview = activeAlerts.mapNotNull { it.alert.lastAutomaticAttemptAt }.maxOrNull()
    val nextReview = activeAlerts.mapNotNull { it.alert.nextDueAt }.minOrNull()
    val selectedLogText = if (selectedLog == "Conexión") probeState.accountDiagnosticLog else probeState.flightDiagnosticLog
    val selectedLogClear = if (selectedLog == "Conexión") onClearConnectionLog else onClearFlightLog

    AppPage {
        StandardBack("Diagnóstico", onBack, "Revisa el funcionamiento de la app.")

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Programación automática", style = MaterialTheme.typography.titleLarge)
                StatusPill(if (schedulingStatus.contains("programad", true) || schedulingStatus.contains("registrado", true)) "Programada" else "Revisar")
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Última revisión", color = FlightColors.SecondaryText)
                Text(lastReview?.let(::formatLocalFull) ?: "Sin revisiones")
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Próxima estimada", color = FlightColors.SecondaryText)
                Text(nextReview?.let(::formatLocalFull) ?: if (activeAlerts.isEmpty()) "Sin alertas activas" else "Según Android")
            }
            SmallDivider()
            Text("Android puede aplazarla por ahorro de batería, Doze o falta de red.", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
            OutlinedButton(onClick = onCheckScheduling, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("check-scheduling")) {
                VectorGlyph(com.flightnotifications.app.R.drawable.ic_calendar, tint = FlightColors.Primary)
                Text("Comprobar programación", modifier = Modifier.padding(start = 8.dp))
            }
            Text("No consume consultas de vuelos.", style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
            schedulingStatus.takeIf { it.contains("bloquead", true) || it.contains("falló", true) || it.contains("No se pudo", true) }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        AppCard(contentPadding = 8.dp, modifier = Modifier.testTag("manual-probe-card")) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { probeExpanded = !probeExpanded }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VectorGlyph(com.flightnotifications.app.R.drawable.ic_flight, tint = FlightColors.Primary, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f)) {
                    Text("Prueba manual de vuelos", style = MaterialTheme.typography.titleLarge)
                    Text("CTG ↔ CLO · Fechas y directos", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
                }
                Text(if (probeExpanded) "⌃" else "⌄", style = MaterialTheme.typography.headlineSmall, color = FlightColors.SecondaryText)
            }
            if (probeExpanded) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        DatePickerButton("Ida", probeState.outboundDate, LocalDate.now(ZoneId.of("America/Bogota")), Modifier.weight(1f), onOutboundDate)
                        DatePickerButton("Regreso", probeState.returnDate, probeState.outboundDate.plusDays(1), Modifier.weight(1f), onReturnDate)
                    }
                    val criteria = SearchCriteria(probeState.outboundDate, probeState.returnDate, probeState.directOnly)
                    criteria.validationError()?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Solo vuelos directos", style = MaterialTheme.typography.titleMedium)
                            Text("Desactívalo para permitir escalas.", color = FlightColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                        }
                        AppSwitch(probeState.directOnly, onDirectOnly, enabled = !probeState.isRunningProbe)
                    }
                    Text("1 adulto · Económica · ida y vuelta · COP · todas las aerolíneas", style = MaterialTheme.typography.bodyMedium)
                    probeState.budget?.let { budget ->
                        Text("Presupuesto local: ${budget.usedInWindow}/${budget.maximumRequests} GET · ${budget.remainingInWindow} disponibles", style = MaterialTheme.typography.bodyMedium)
                        if (!budget.clockTrusted) Text("El reloj del dispositivo no es confiable; los GET de vuelos están bloqueados.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onStartProbe,
                            enabled = probeState.credentialConfigured && !probeState.isRunningProbe && !probeState.isCheckingAccount && criteria.validationError() == null,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("start-flight-probe"),
                        ) { Text("Probar ida y vuelta") }
                        if (probeState.isRunningProbe) OutlinedButton(onClick = onCancelProbe) { Text("Cancelar") }
                    }
                    if (probeState.isRunningProbe || probeState.isCheckingAccount) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(if (probeState.isRunningProbe) "Consultando vuelos…" else "Comprobando cuenta…")
                        }
                    }
                    probeState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    probeState.outcome?.let { ProbeOutcomeCard(it) }
                    Text("La prueba manual es opcional y puede consumir hasta 2 GET de vuelos.", style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Registro", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Conexión", "Vuelos").forEach { tab ->
                    FilterChip(selected = selectedLog == tab, onClick = { selectedLog = tab }, label = { Text(tab) }, modifier = Modifier.weight(1f).testTag("log-tab-$tab"))
                }
            }
            OutlinedTextField(
                value = selectedLogText,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 340.dp).testTag("diagnostic-log-text"),
                label = { Text("Log de diagnóstico") },
                placeholder = { Text("Aún no hay eventos registrados.") },
                readOnly = true,
                minLines = 9,
                maxLines = 14,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Registro de ${selectedLog.lowercase()}", selectedLogText))
                        Toast.makeText(context, "Registro copiado", Toast.LENGTH_SHORT).show()
                    },
                    enabled = selectedLogText.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Copiar") }
                OutlinedButton(
                    onClick = selectedLogClear,
                    enabled = selectedLogText.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Limpiar") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VectorGlyph(com.flightnotifications.app.R.drawable.ic_check_circle, tint = FlightColors.Success, modifier = Modifier.size(20.dp))
                Text("Sin API keys ni tokens.", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
            }
            SmallDivider()
            Text("Cambiar de pestaña no hace consultas. Las pruebas manuales pueden consumir cuota.", style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
        }
    }

    if (probeState.showFlightConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissProbe,
            title = { Text("Autorizar prueba de vuelos") },
            text = {
                Text(
                    "CTG → CLO ${probeState.outboundDate}; regreso ${probeState.returnDate}; ${if (probeState.directOnly) "directos" else "con escalas permitidas"}. " +
                        "Se comprobará primero la cuenta y luego se iniciarán hasta 2 GET de vuelos si hay capacidad. Cancelar inicia 0 GET de vuelos.",
                )
            },
            confirmButton = { TextButton(onClick = onConfirmProbe) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = onDismissProbe) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ProbeOutcomeCard(outcome: com.flightnotifications.app.domain.model.ProbeOutcome) {
    AppCard(contentPadding = 14.dp) {
        Text("Resultado: ${outcome.statusLabel}", style = MaterialTheme.typography.titleLarge)
        Text(outcome.detail, style = MaterialTheme.typography.bodyMedium)
        Text("Criterios: CTG → CLO ${outcome.criteria.outboundDate}; regreso ${outcome.criteria.returnDate}; ${if (outcome.criteria.directOnly) "directos" else "escalas permitidas"}.", style = MaterialTheme.typography.bodySmall)
        Text("GET de vuelos iniciados: ${outcome.flightGetsStarted} de 2.", style = MaterialTheme.typography.bodySmall)
        outcome.outbound?.let {
            Text("Ida: ${it.segments.firstOrNull()?.departure?.code ?: "CTG"} → ${it.segments.lastOrNull()?.arrival?.code ?: "CLO"} · ${it.carriers.joinToString(" / ").ifBlank { "Operador no informado" }} · ${formatStops(it.segments.size - 1)}", style = MaterialTheme.typography.bodyMedium)
        }
        outcome.selectedReturn?.let {
            Text("Regreso: ${it.segments.firstOrNull()?.departure?.code ?: "CLO"} → ${it.segments.lastOrNull()?.arrival?.code ?: "CTG"} · ${it.carriers.joinToString(" / ").ifBlank { "Operador no informado" }} · ${formatStops(it.segments.size - 1)}", style = MaterialTheme.typography.bodyMedium)
        }
        outcome.verifiedRoundTripPriceCop?.let {
            Text("Tarifa total observada: ${formatCop(it)} · ida y vuelta", style = MaterialTheme.typography.titleMedium, color = FlightColors.Primary)
        }
        Text("La tarifa puede cambiar al reservar. No se suman importes por etapa.", style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
    }
}
