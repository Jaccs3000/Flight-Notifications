package com.flightnotifications.app.presentation

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.repository.AlertWithResult
import com.flightnotifications.app.domain.model.VerifiedRoundTripOffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private data class AlertEditorFields(
    val name: String,
    val origin: String,
    val destination: String,
    val outbound: LocalDate,
    val returning: LocalDate,
    val maximum: String,
    val frequency: Int,
    val direct: Boolean,
)

@Composable
fun FlightAlertsSection(viewModel: FlightAlertsViewModel, maximumLocalGets: Int) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsAllowed by remember { mutableStateOf(viewModel.notificationsAllowed()) }
    var editorId by remember { mutableStateOf<String?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var pendingEnableId by remember { mutableStateOf<String?>(null) }
    var expandedAlertId by remember { mutableStateOf<String?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = pendingEnableId
        pendingEnableId = null
        notificationsAllowed = viewModel.notificationsAllowed()
        if (granted && id != null) viewModel.setEnabled(id, true)
        else if (!granted) viewModel.notificationPermissionDenied()
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = viewModel.notificationsAllowed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val editorAlert = editorId?.let { target -> state.alerts.firstOrNull { it.alert.id == target }?.alert }
    val activeCount = state.alerts.count { it.alert.enabled && !LocalDate.now(COLOMBIA_ZONE).isAfter(LocalDate.parse(it.alert.outboundDate)) }
    val defaultDate = LocalDate.now(COLOMBIA_ZONE).plusDays(30)
    var fields by remember(editorId, editorOpen) {
        mutableStateOf(
            editorAlert?.let {
                AlertEditorFields(it.name, it.originIata, it.destinationIata, LocalDate.parse(it.outboundDate), LocalDate.parse(it.returnDate), it.maxTotalCop.toString(), it.frequencyHours, it.directOnly)
            } ?: AlertEditorFields("", "", "", defaultDate, defaultDate.plusDays(3), "500000", 24, true),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Alertas de vuelos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("$activeCount activas · ${state.alerts.size} configuradas · rutas nacionales en Colombia", style = MaterialTheme.typography.bodyMedium)
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Alertas guardadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { editorId = null; editorOpen = true }, modifier = Modifier.testTag("new-alert")) { Text("Nueva alerta") }
                }
                if (state.alerts.isEmpty()) {
                    Text("Todavía no hay alertas. La app no inicia búsquedas hasta que guardes y actives una.")
                }
                state.alerts.forEach { item ->
                    val alert = item.alert
                    val expired = LocalDate.now(COLOMBIA_ZONE).isAfter(LocalDate.parse(alert.outboundDate))
                    AlertSummaryCard(
                        item = item,
                        expanded = expandedAlertId == alert.id || state.selectedAlertId == alert.id,
                        isRunning = state.runningAlertId == alert.id,
                        expired = expired,
                        onExpand = {
                            expandedAlertId = if (expandedAlertId == alert.id) null else alert.id
                            viewModel.clearSelectedAlert()
                        },
                        onEdit = { editorId = alert.id; editorOpen = true },
                        onDelete = { viewModel.requestDelete(alert.id) },
                        onSearch = { viewModel.requestReview(alert.id) },
                        onToggle = { enabled ->
                            if (!enabled) viewModel.setEnabled(alert.id, false)
                            else if (viewModel.notificationsAllowed()) viewModel.setEnabled(alert.id, true)
                            else if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                pendingEnableId = alert.id
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                                viewModel.notificationPermissionDenied()
                            }
                        },
                        onShowCatalog = { showCatalog = true },
                    )
                    if (item != state.alerts.lastOrNull()) HorizontalDivider()
                }
                val active = state.alerts.map { it.alert }.filter { it.enabled && !LocalDate.now(COLOMBIA_ZONE).isAfter(LocalDate.parse(it.outboundDate)) }
                val monthlyLow = active.sumOf { 720L / it.frequencyHours * 2L }
                val monthlyHigh = active.sumOf { 720L / it.frequencyHours * 4L }
                Text("Consumo automático estimado: $monthlyLow–$monthlyHigh GET de vuelos por 30 días, más revisiones manuales. Es una proyección; el máximo local actual es $maximumLocalGets.", style = MaterialTheme.typography.bodySmall)
                if (monthlyLow > maximumLocalGets || monthlyHigh > maximumLocalGets) {
                    Text("La configuración activa supera el presupuesto local estimado; algunas revisiones podrían quedar pendientes. Se respetará el límite global de cuota.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { showCatalog = true }) { Text("Catálogo informativo de aerolíneas") }
            }
        }

        if (activeCount > 0 && !notificationsAllowed) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notificaciones desactivadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Las revisiones automáticas están suspendidas para no gastar cuota en avisos que Android no puede mostrar. Las búsquedas manuales siguen disponibles, pero tampoco podrán mostrar avisos mientras el permiso o el canal estén desactivados.")
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                    ) { Text("Abrir ajustes de notificaciones") }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Avisos enviados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Hasta 100 avisos locales. La tarifa más baja puede notificarse de nuevo en cada revisión, aunque el precio no cambie.", style = MaterialTheme.typography.bodySmall)
                if (state.notifications.isEmpty()) Text("No se han enviado avisos.")
                state.notifications.forEach { notice ->
                    TextButton(onClick = { viewModel.setNotificationOpenAlert(notice.alertId) }) {
                        Text("${notice.routeSummary} · ${formatCop(notice.priceCop)} · ${formatLocal(notice.sentAt)}")
                    }
                }
                if (state.notifications.isNotEmpty()) OutlinedButton(onClick = viewModel::requestClearNotifications) { Text("Limpiar avisos") }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Programación automática", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(state.schedulingStatus)
                Text("Un solo coordinador periódico con red conectada. Las frecuencias son objetivos: Android puede aplazar la ejecución por ahorro de batería, Doze, falta de red o apagado del teléfono.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = viewModel::checkScheduling) { Text("Comprobar programación · sin llamadas API") }
            }
        }

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("alert-feedback-message")) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { editorOpen = false },
            title = { Text(if (editorAlert == null) "Crear alerta" else "Editar alerta") },
            text = {
                Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(fields.name, { fields = fields.copy(name = it.take(80)) }, label = { Text("Nombre (3–80 caracteres)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("alert-name"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(fields.origin, { fields = fields.copy(origin = it.uppercase().take(3)) }, label = { Text("Origen IATA") }, singleLine = true, modifier = Modifier.weight(1f).testTag("alert-origin"), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                        OutlinedTextField(fields.destination, { fields = fields.copy(destination = it.uppercase().take(3)) }, label = { Text("Destino IATA") }, singleLine = true, modifier = Modifier.weight(1f).testTag("alert-destination"), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                    }
                    Text("Solo aeropuertos incluidos en el catálogo nacional local.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateEditorButton("Ida", fields.outbound, LocalDate.now(COLOMBIA_ZONE), Modifier.weight(1f)) { selected ->
                            fields = fields.copy(outbound = selected, returning = if (fields.returning.isAfter(selected)) fields.returning else selected.plusDays(1))
                        }
                        DateEditorButton("Regreso", fields.returning, fields.outbound.plusDays(1), Modifier.weight(1f)) { fields = fields.copy(returning = it) }
                    }
                    OutlinedTextField(
                        fields.maximum,
                        { fields = fields.copy(maximum = it.filter(Char::isDigit).take(12)) },
                        label = { Text("Máximo total ida y vuelta · COP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("alert-maximum"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    FrequencyField(fields.frequency) { fields = fields.copy(frequency = it) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { fields = fields.copy(direct = !fields.direct) }) {
                        Checkbox(checked = fields.direct, onCheckedChange = { fields = fields.copy(direct = it) }, modifier = Modifier.testTag("alert-direct-only"))
                        Column {
                            Text("Solo vuelos directos")
                            Text("Desactivado permite escalas en ambos sentidos.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("1 adulto · económica · ida y vuelta · COP · todas las aerolíneas. No se envían filtros de compañías ni compra/reserva.", style = MaterialTheme.typography.bodySmall)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("alert-form-error")) }
                    state.alerts.map { it.alert }.filter { it.enabled }.let { active ->
                        val existing = active.filterNot { it.id == editorId }
                        val projected = existing.sumOf { 720L / it.frequencyHours * 4L } + 720L / fields.frequency * 4L
                        Text("Estimación alta con esta configuración: $projected GET/30 días si se activa. Presupuesto local: $maximumLocalGets; puedes guardarla pausada.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val maximum = fields.maximum.toLongOrNull() ?: 0L
                    val input = AlertInput(editorId, fields.name, fields.origin, fields.destination, fields.outbound, fields.returning, maximum, fields.frequency, fields.direct)
                    viewModel.save(input) { editorOpen = false }
                }, modifier = Modifier.testTag("save-alert")) { Text("Guardar pausada") }
            },
            dismissButton = { TextButton(onClick = { editorOpen = false; }) { Text("Cancelar") } },
        )
    }

    val reviewAlert = state.reviewConfirmationId?.let { id -> state.alerts.firstOrNull { it.alert.id == id }?.alert }
    if (reviewAlert != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissReviewConfirmation,
            title = { Text("Revisar precios ahora") },
            text = { Text("${reviewAlert.originIata} ↔ ${reviewAlert.destinationIata}, ${reviewAlert.outboundDate} / ${reviewAlert.returnDate}. Se comprobará la cuenta una vez y, si hay capacidad, se iniciarán hasta 4 GET de vuelos (1 inicial + hasta 3 regresos asociados). Cancelar inicia 0 GET de vuelos. La búsqueda manual no activa una alerta pausada.") },
            confirmButton = { TextButton(onClick = viewModel::confirmReview) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissReviewConfirmation) { Text("Cancelar") } },
        )
    }

    state.deletingAlertId?.let { id ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Eliminar alerta") },
            text = { Text("Se eliminarán los resultados y registros de avisos de esta alerta. El presupuesto global de GET, la clave y otras alertas se conservan.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("confirm-delete-alert")) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Cancelar") } },
        )
    }

    if (state.showClearNotificationsConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearNotifications,
            title = { Text("Limpiar avisos visibles") },
            text = { Text("Se vaciará la lista local y se cerrarán las notificaciones visibles. Esto no impedirá que la tarifa más baja vuelva a notificarse en la próxima revisión.") },
            confirmButton = { TextButton(onClick = viewModel::confirmClearNotifications) { Text("Limpiar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissClearNotifications) { Text("Cancelar") } },
        )
    }
    if (showCatalog) AirlineCatalogDialog(viewModel) { showCatalog = false }
}

@Composable
private fun AlertSummaryCard(
    item: AlertWithResult,
    expanded: Boolean,
    isRunning: Boolean,
    expired: Boolean,
    onExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSearch: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onShowCatalog: () -> Unit,
) {
    val alert = item.alert
    val result = item.result
    val criteria = "${alert.originIata} ↔ ${alert.destinationIata} · ${alert.outboundDate} / ${alert.returnDate}"
    Column(Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(alert.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(criteria, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = alert.enabled && !expired, onCheckedChange = onToggle, enabled = !isRunning && !expired, modifier = Modifier.testTag("toggle-alert-${alert.id}"))
        }
        Text("Máximo ${formatCop(alert.maxTotalCop)} · cada ${alert.frequencyHours} h · ${if (alert.directOnly) "directos" else "escalas permitidas"} · ${when { expired -> "vencida"; alert.enabled -> "activa"; else -> "pausada" }}")
        if (alert.enabled && !expired && alert.nextDueAt != null) {
            Text("Próxima revisión prevista: ${formatLocal(alert.nextDueAt)} · Android puede aplazarla.", style = MaterialTheme.typography.bodySmall)
        }
        if (result != null) {
            Text("Última revisión: ${result.status} · ${formatLocal(result.lastAttemptAt)} · ${result.flightGetsStarted} GET")
            if (result.lastVerifiedTotalCop != null) {
                val previous = !isCurrentVerification(alert, result)
                Text("${if (previous) "Precio verificado anterior (no confirmado en la última revisión)" else "Último precio verificado"}: ${formatCop(result.lastVerifiedTotalCop)}${result.lastVerifiedAt?.let { " · ${formatLocal(it)}" }.orEmpty()}", fontWeight = FontWeight.Medium)
            }
        } else Text("Aún no hay resultados de búsqueda.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onSearch, enabled = !isRunning) { Text(if (isRunning) "Consultando…" else "Buscar") }
            TextButton(onClick = onEdit, enabled = !isRunning, modifier = Modifier.testTag("edit-alert-${alert.id}")) { Text("Editar") }
            TextButton(onClick = onDelete, enabled = !isRunning, modifier = Modifier.testTag("delete-alert-${alert.id}")) { Text("Eliminar") }
        }
        if (expanded) AlertResultDetails(alert, result, onShowCatalog)
    }
}

@Composable
private fun AlertResultDetails(alert: FlightAlertEntity, result: com.flightnotifications.app.data.local.FlightAlertResultEntity?, onShowCatalog: () -> Unit) {
    if (result == null) return
    val context = LocalContext.current
    val airlines = result.airlinesJson?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()
    val verifiedOffers = result.verifiedOffersJson
        ?.let { runCatching { Json.decodeFromString<List<VerifiedRoundTripOffer>>(it) }.getOrNull() }
        ?.takeIf { it.isNotEmpty() }
        ?: result.selectedOfferJson
            ?.let { runCatching { listOf(Json.decodeFromString<VerifiedRoundTripOffer>(it)) }.getOrNull() }
            .orEmpty()
    val currentVerification = isCurrentVerification(alert, result)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 8.dp)) {
        HorizontalDivider()
        Text("Detalle de la última consulta", fontWeight = FontWeight.SemiBold)
        Text("Criterios de esa consulta: ${result.attemptCriteria?.let(::formatCriteriaKey) ?: "no disponibles"}.")
        Text("Máximo aplicado entonces: ${formatCop(result.attemptMaximumCop ?: alert.maxTotalCop)} · máximo actual: ${formatCop(alert.maxTotalCop)}.")
        Text("Estado: ${result.status}${result.lastError?.let { " · motivo: $it" }.orEmpty()}")
        result.networkReceivedAt?.let { Text("Respuesta recibida: ${formatLocal(it)}") }
        Text("Exploración de salidas: ${result.candidatesConsidered} de hasta ${result.candidateLimit}; ${if (result.explorationLimited) "parcial" else "sin más candidatos informados"}.")
        if (result.lastVerifiedTotalCop != null) {
            Text("${if (currentVerification) "Total verificado en esta consulta" else "Último total verificado previo; no confirmado con los criterios actuales"}: ${formatCop(result.lastVerifiedTotalCop)}")
            if (!currentVerification) {
                Text("Criterios del último total verificado: ${result.verifiedCriteria?.let(::formatCriteriaKey) ?: "no disponibles"} · ${result.lastVerifiedAt?.let(::formatLocal) ?: "fecha no disponible"}.")
            }
            if (verifiedOffers.isNotEmpty()) {
                Text(if (currentVerification) "Opciones verificadas en esta consulta (${verifiedOffers.size})" else "Opciones de la última consulta verificada (${verifiedOffers.size})", fontWeight = FontWeight.Medium)
                verifiedOffers.forEachIndexed { index, offer ->
                    OfferSummary(offer, if (currentVerification) "Opción ${index + 1}" else "Dato previo · opción ${index + 1}")
                }
            }
        }
        if (airlines.isNotEmpty()) {
            Text("Aerolíneas encontradas en esta búsqueda (${airlines.size})", fontWeight = FontWeight.Medium)
            airlines.forEach { airline -> Text("• $airline") }
        } else Text("No se recibieron nombres de aerolíneas en la última respuesta.")
        if (!currentVerification) {
            Text("No se presenta un total de esta consulta como verificado. Los importes por etapa no se suman.", style = MaterialTheme.typography.bodySmall)
        }
        result.googleFlightsUrl?.let { url ->
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) {
                Text("Abrir Google Flights para verificar")
            }
        }
        OutlinedButton(onClick = onShowCatalog) { Text("Catálogo informativo de aerolíneas") }
    }
}

@Composable
private fun OfferSummary(offer: VerifiedRoundTripOffer, label: String) {
    val outStops = (offer.outbound.size - 1).coerceAtLeast(0)
    val backStops = (offer.returning.size - 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontWeight = FontWeight.Medium)
        Text("Ida: ${offer.outbound.firstOrNull()?.fromIata ?: "?"} → ${offer.outbound.lastOrNull()?.toIata ?: "?"} · ${offer.outboundAirline} · ${if (outStops == 0) "directo" else "$outStops escala(s)"}")
        Text("Regreso: ${offer.returning.firstOrNull()?.fromIata ?: "?"} → ${offer.returning.lastOrNull()?.toIata ?: "?"} · ${offer.returnAirline} · ${if (backStops == 0) "directo" else "$backStops escala(s)"}")
        Text("Total de ida y vuelta observado: ${formatCop(offer.totalCop)}. Verifica condiciones y disponibilidad antes de comprar.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DateEditorButton(label: String, date: LocalDate, minimum: LocalDate, modifier: Modifier = Modifier, onSelect: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val dialog = DatePickerDialog(context, { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day)) }, date.year, date.monthValue - 1, date.dayOfMonth)
        dialog.datePicker.minDate = minimum.atStartOfDay(COLOMBIA_ZONE).toInstant().toEpochMilli()
        dialog.show()
    }, modifier = modifier) { Text("$label\n$date") }
}

@Composable
private fun FrequencyField(value: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text("Frecuencia automática: cada $value h") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(6, 8, 12, 24).forEach { frequency ->
                DropdownMenuItem(text = { Text("Cada $frequency horas") }, onClick = { onSelect(frequency); expanded = false })
            }
        }
    }
}

@Composable
private fun AirlineCatalogDialog(viewModel: FlightAlertsViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aerolíneas nacionales · ${viewModel.airlineCatalog.reviewedAt}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                viewModel.airlineCatalog.airlines.forEach { Text("• ${it.name} (${it.code})") }
                Text(viewModel.airlineCatalog.source, style = MaterialTheme.typography.bodySmall)
                Text("Catálogo informativo, no exhaustivo. No filtra la búsqueda ni garantiza tarifas/cobertura; cualquier operador devuelto por SerpApi aparece en los resultados.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

private fun formatCop(amount: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).apply {
    currency = Currency.getInstance("COP")
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}.format(amount)

private fun isCurrentVerification(alert: FlightAlertEntity, result: com.flightnotifications.app.data.local.FlightAlertResultEntity): Boolean =
    result.status == "VERIFICADO" && result.verifiedCriteria != null &&
        result.verifiedCriteria == result.attemptCriteria && result.verifiedCriteria == criteriaKey(alert) &&
        result.attemptMaximumCop == alert.maxTotalCop

private fun criteriaKey(alert: FlightAlertEntity): String =
    "${alert.originIata}-${alert.destinationIata}|${alert.outboundDate}|${alert.returnDate}|direct=${alert.directOnly}"

private fun formatCriteriaKey(value: String): String {
    val parts = value.split('|')
    val route = parts.getOrNull(0)?.replace('-', '→') ?: return "no disponibles"
    val outbound = parts.getOrNull(1) ?: return value
    val returning = parts.getOrNull(2) ?: return value
    val direct = parts.getOrNull(3)?.substringAfter('=')?.toBooleanStrictOrNull()
    return "$route · ida $outbound · regreso $returning · ${if (direct == true) "directos" else if (direct == false) "escalas permitidas" else "filtros no disponibles"}"
}

private fun formatLocal(epochMillis: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'COT'")
    .withZone(COLOMBIA_ZONE).format(Instant.ofEpochMilli(epochMillis))

private val COLOMBIA_ZONE: ZoneId = ZoneId.of("America/Bogota")
