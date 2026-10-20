package com.flightnotifications.app.presentation

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.repository.AlertWithResult
import com.flightnotifications.app.domain.validation.ColombianAirport
import com.flightnotifications.app.domain.validation.ColombianAirportCatalog
import com.flightnotifications.app.domain.model.VerifiedRoundTripOffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.text.NumberFormat
import java.text.Normalizer
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
    val originQuery: String = "",
    val destinationQuery: String = "",
)

private fun airportLabel(airport: ColombianAirport): String = "${airport.city.lowercase(Locale.forLanguageTag("es-CO")).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-CO")) }} · ${airport.iata}"

private fun normalizeAirportQuery(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase(Locale.ROOT)
    .trim()

@Composable
private fun AirportAutocompleteField(
    label: String,
    selectedCode: String,
    query: String,
    catalog: ColombianAirportCatalog,
    tag: String,
    onQueryChange: (String) -> Unit,
    onSelect: (ColombianAirport) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAirport = catalog.find(selectedCode)
    val value = query.ifBlank { selectedAirport?.let(::airportLabel).orEmpty() }
    val normalized = normalizeAirportQuery(query)
    val suggestions = remember(normalized, catalog.airports) {
        if (normalized.isBlank()) {
            val popular = listOf("BOG", "MDE", "CTG", "CLO", "BAQ", "SMR", "ADZ", "PEI")
            popular.mapNotNull(catalog::find)
        } else {
            catalog.airports
                .filter { airport ->
                    listOf(airport.iata, airport.city, airport.name, airport.department)
                        .any { normalizeAirportQuery(it).contains(normalized) }
                }
                .sortedWith(
                    compareBy<ColombianAirport> {
                        when {
                            normalizeAirportQuery(it.iata) == normalized -> 0
                            normalizeAirportQuery(it.city).startsWith(normalized) -> 1
                            normalizeAirportQuery(it.name).startsWith(normalized) -> 2
                            else -> 3
                        }
                    }.thenBy { it.city },
                )
                .take(7)
        }
    }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { typed -> onQueryChange(typed); expanded = true },
            label = { Text(label) },
            placeholder = { Text("Ciudad, aeropuerto o IATA") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            supportingText = {
                Text(if (selectedAirport != null && query.isBlank()) "${selectedAirport.name} · ${selectedAirport.iata}" else "Busca por ciudad, aeropuerto o código IATA")
            },
        )
        DropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
        ) {
            suggestions.forEach { airport ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${airport.city} · ${airport.iata}", fontWeight = FontWeight.Medium)
                            Text(airport.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    },
                    onClick = { onSelect(airport); expanded = false },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
    }
}

@Composable
private fun FlightAlertsLegacySection(viewModel: FlightAlertsViewModel, maximumLocalGets: Int) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsAllowed by remember { mutableStateOf(viewModel.notificationsAllowed()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = viewModel.notificationsAllowed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var editorId by remember { mutableStateOf<String?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var pendingEnableId by remember { mutableStateOf<String?>(null) }
    var expandedAlertId by remember { mutableStateOf<String?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    val activeCount = state.alerts.count { it.alert.enabled && !LocalDate.now(COLOMBIA_ZONE).isAfter(LocalDate.parse(it.alert.outboundDate)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = viewModel.notificationsAllowed()
        val id = pendingEnableId
        pendingEnableId = null
        if (granted && id != null) viewModel.setEnabled(id, true)
        else if (!granted) viewModel.notificationPermissionDenied()
    }

    val editorAlert = editorId?.let { target -> state.alerts.firstOrNull { it.alert.id == target }?.alert }
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

        if (activeCount > 0 && !notificationsAllowed) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Las alertas siguen configuradas, pero Android tiene desactivadas las notificaciones. Se suspenden las búsquedas automáticas para no consumir cuota; la búsqueda manual sigue disponible.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }, modifier = Modifier.fillMaxWidth().primaryButtonTarget()) { Text("Abrir ajustes de notificaciones") }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Alertas guardadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { editorId = null; editorOpen = true }, modifier = Modifier.testTag("new-alert").primaryButtonTarget()) { Text("Nueva alerta") }
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
                            else if (notificationsAllowed) viewModel.setEnabled(alert.id, true)
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
                TextButton(onClick = { showCatalog = true }, modifier = Modifier.secondaryButtonTarget()) { Text("Catálogo informativo de aerolíneas") }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Avisos enviados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Hasta 100 avisos locales. La tarifa más baja puede notificarse de nuevo en cada revisión, aunque el precio no cambie.", style = MaterialTheme.typography.bodySmall)
                if (state.notifications.isEmpty()) Text("No se han enviado avisos.")
                state.notifications.forEach { notice ->
                    TextButton(onClick = { viewModel.setNotificationOpenAlert(notice.alertId) }, modifier = Modifier.secondaryButtonTarget()) {
                        Text("${notice.routeSummary} · ${formatCop(notice.priceCop)} · ${formatLocal(notice.sentAt)}")
                    }
                }
                if (state.notifications.isNotEmpty()) OutlinedButton(onClick = viewModel::requestClearNotifications, modifier = Modifier.primaryButtonTarget()) { Text("Limpiar avisos") }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Programación automática", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(state.schedulingStatus)
                Text("Un solo coordinador periódico con red conectada. Las frecuencias son objetivos: Android puede aplazar la ejecución por ahorro de batería, Doze, falta de red o apagado del teléfono.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = viewModel::checkScheduling, modifier = Modifier.primaryButtonTarget()) { Text("Comprobar programación · sin llamadas API") }
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AirportAutocompleteField(
                            "Origen", fields.origin, fields.originQuery, viewModel.airportCatalog, "alert-origin",
                            onQueryChange = { fields = fields.copy(origin = "", originQuery = it) },
                            onSelect = { fields = fields.copy(origin = it.iata, originQuery = "") },
                        )
                        AirportAutocompleteField(
                            "Destino", fields.destination, fields.destinationQuery, viewModel.airportCatalog, "alert-destination",
                            onQueryChange = { fields = fields.copy(destination = "", destinationQuery = it) },
                            onSelect = { fields = fields.copy(destination = it.iata, destinationQuery = "") },
                        )
                    }
                    Text("Solo aeropuertos incluidos en el catálogo nacional local.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateEditorButton("Ida", fields.outbound, LocalDate.now(COLOMBIA_ZONE), Modifier.weight(1f)) { selected ->
                            val nextReturn = if (fields.returning.isAfter(selected)) fields.returning else selected.plusDays(1)
                            fields = fields.copy(outbound = selected, returning = nextReturn)
                            Handler(Looper.getMainLooper()).post {
                                openDatePicker(context, nextReturn, selected.plusDays(1)) { returning ->
                                    fields = fields.copy(returning = returning)
                                }
                            }
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
                        Checkbox(checked = fields.direct, onCheckedChange = { fields = fields.copy(direct = it) }, modifier = Modifier.testTag("alert-direct-only").sizeIn(minWidth = 48.dp, minHeight = 48.dp))
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
                        Text("Estimación alta con esta configuración: $projected GET/30 días si se activa. Presupuesto local: $maximumLocalGets; quedará activa al guardarla.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val maximum = fields.maximum.toLongOrNull() ?: 0L
                    val input = AlertInput(editorId, fields.name, fields.origin, fields.destination, fields.outbound, fields.returning, maximum, fields.frequency, fields.direct)
                    viewModel.save(input) { editorOpen = false }
                }, modifier = Modifier.testTag("save-alert").primaryButtonTarget()) { Text("Guardar activa") }
            },
            dismissButton = { TextButton(onClick = { editorOpen = false; }, modifier = Modifier.secondaryButtonTarget()) { Text("Cancelar") } },
        )
    }

    val reviewAlert = state.reviewConfirmationId?.let { id -> state.alerts.firstOrNull { it.alert.id == id }?.alert }
    if (reviewAlert != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissReviewConfirmation,
            title = { Text("Revisar precios ahora") },
            text = { Text("${reviewAlert.originIata} ↔ ${reviewAlert.destinationIata}, ${reviewAlert.outboundDate} / ${reviewAlert.returnDate}. Se comprobará la cuenta una vez y, si hay capacidad, se iniciarán hasta 4 GET de vuelos (1 inicial + hasta 3 regresos asociados). Cancelar inicia 0 GET de vuelos. La búsqueda manual no activa una alerta pausada.") },
            confirmButton = { TextButton(onClick = viewModel::confirmReview, modifier = Modifier.secondaryButtonTarget()) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissReviewConfirmation, modifier = Modifier.secondaryButtonTarget()) { Text("Cancelar") } },
        )
    }

    state.deletingAlertId?.let { id ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Eliminar alerta") },
            text = { Text("Se eliminarán los resultados y registros de avisos de esta alerta. El presupuesto global de GET, la clave y otras alertas se conservan.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("confirm-delete-alert").secondaryButtonTarget()) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete, modifier = Modifier.secondaryButtonTarget()) { Text("Cancelar") } },
        )
    }

    if (state.showClearNotificationsConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearNotifications,
            title = { Text("Limpiar avisos visibles") },
            text = { Text("Se vaciará la lista local y se cerrarán las notificaciones visibles. Esto no impedirá que la tarifa más baja vuelva a notificarse en la próxima revisión.") },
            confirmButton = { TextButton(onClick = viewModel::confirmClearNotifications, modifier = Modifier.secondaryButtonTarget()) { Text("Limpiar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissClearNotifications, modifier = Modifier.secondaryButtonTarget()) { Text("Cancelar") } },
        )
    }
    if (showCatalog) AirlineCatalogDialog(viewModel) { showCatalog = false }
}

private enum class AlertListFilter { ALL, ACTIVE, PAUSED }

@Composable
fun FlightAlertsSection(
    viewModel: FlightAlertsViewModel,
    maximumLocalGets: Int,
    onEditAlert: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsAllowed by remember { mutableStateOf(viewModel.notificationsAllowed()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = viewModel.notificationsAllowed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var pendingEnableId by remember { mutableStateOf<String?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(AlertListFilter.ALL) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = viewModel.notificationsAllowed()
        val id = pendingEnableId
        pendingEnableId = null
        if (granted && id != null) viewModel.setEnabled(id, true)
        else if (!granted) viewModel.notificationPermissionDenied()
    }
    val activeAlerts = state.alerts.filter {
        it.alert.enabled && !LocalDate.now(COLOMBIA_ZONE).isAfter(LocalDate.parse(it.alert.outboundDate))
    }
    val activeCount = activeAlerts.size
    val visibleAlerts = state.alerts.filter { item ->
        val active = item.alert.enabled && !LocalDate.now(COLOMBIA_ZONE).isAfter(LocalDate.parse(item.alert.outboundDate))
        when (filter) {
            AlertListFilter.ALL -> true
            AlertListFilter.ACTIVE -> active
            AlertListFilter.PAUSED -> !active
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Mis alertas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("$activeCount activa${if (activeCount == 1) "" else "s"} · ${state.alerts.size} guardada${if (state.alerts.size == 1) "" else "s"}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AlertListFilter.ALL to "Todas", AlertListFilter.ACTIVE to "Activas", AlertListFilter.PAUSED to "Pausadas").forEach { (value, label) ->
        FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) }, modifier = Modifier.chipTarget())
            }
        }

        if (activeCount > 0 && !notificationsAllowed) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Android tiene desactivadas las notificaciones. Las revisiones automáticas están suspendidas para no consumir cuota.", color = MaterialTheme.colorScheme.onErrorContainer)
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }, modifier = Modifier.fillMaxWidth().primaryButtonTarget()) { Text("Abrir ajustes de notificaciones") }
                }
            }
        }

        if (visibleAlerts.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.alerts.isEmpty()) "Aún no tienes alertas" else "No hay alertas en este filtro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Crea una alerta para vigilar una ruta nacional y un rango de fechas.", color = MaterialTheme.colorScheme.secondary)
                }
            }
        } else {
            visibleAlerts.forEach { item ->
                val alert = item.alert
                val expired = LocalDate.now(COLOMBIA_ZONE).isAfter(LocalDate.parse(alert.outboundDate))
                SketchAlertCard(
                    item = item,
                    expired = expired,
                    isRunning = state.runningAlertId == alert.id,
                    onOpenDetail = { onOpenDetail(alert.id) },
                    onEdit = { onEditAlert(alert.id) },
                    onDelete = { viewModel.requestDelete(alert.id) },
                    onSearch = { viewModel.requestReview(alert.id) },
                    onToggle = { enabled ->
                        if (!enabled) viewModel.setEnabled(alert.id, false)
                        else if (notificationsAllowed) viewModel.setEnabled(alert.id, true)
                        else if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            pendingEnableId = alert.id
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            viewModel.notificationPermissionDenied()
                        }
                    },
                )
            }
        }

        val monthlyLow = activeAlerts.sumOf { 720L / it.alert.frequencyHours * 2L }
        val monthlyHigh = activeAlerts.sumOf { 720L / it.alert.frequencyHours * 4L }
        if (activeAlerts.isNotEmpty() && monthlyHigh > maximumLocalGets) {
            Text("Estimación automática: $monthlyLow–$monthlyHigh consultas de vuelos por 30 días. Algunas revisiones podrían esperar capacidad del presupuesto local ($maximumLocalGets).", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("◷", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleLarge)
            Text("Android puede aplazar las revisiones.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("alert-feedback-message")) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }

    val reviewAlert = state.reviewConfirmationId?.let { id -> state.alerts.firstOrNull { it.alert.id == id }?.alert }
    if (reviewAlert != null) AlertDialog(
        onDismissRequest = viewModel::dismissReviewConfirmation,
        title = { Text("Revisar precios ahora") },
        text = { Text("${reviewAlert.originIata} ↔ ${reviewAlert.destinationIata}, ${reviewAlert.outboundDate} / ${reviewAlert.returnDate}. Se comprobará la cuenta primero y, si hay capacidad, se iniciarán hasta 4 GET de vuelos. Cancelar inicia 0 GET. La revisión manual no activa una alerta pausada.") },
        confirmButton = { TextButton(onClick = viewModel::confirmReview, modifier = Modifier.secondaryButtonTarget()) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = viewModel::dismissReviewConfirmation, modifier = Modifier.secondaryButtonTarget()) { Text("Cancelar") } },
    )
    state.deletingAlertId?.let { id -> AlertDialog(
        onDismissRequest = viewModel::dismissDelete,
        title = { Text("Eliminar alerta") },
        text = { Text("Se eliminarán sus resultados y avisos. El presupuesto global, la clave y las demás alertas se conservan.") },
        confirmButton = { TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("confirm-delete-alert").secondaryButtonTarget()) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = viewModel::dismissDelete, modifier = Modifier.secondaryButtonTarget()) { Text("Cancelar") } },
    ) }
    if (showCatalog) AirlineCatalogDialog(viewModel) { showCatalog = false }
}

@Composable
private fun SketchAlertCard(
    item: AlertWithResult,
    expired: Boolean,
    isRunning: Boolean,
    onOpenDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSearch: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val alert = item.alert
    val result = item.result
    val active = alert.enabled && !expired
    val current = result?.let { isCurrentVerification(alert, it) } == true
    val previous = !current && result?.lastVerifiedTotalCop != null
    val selectedOffer = result?.selectedOfferJson?.let { runCatching { Json.decodeFromString<VerifiedRoundTripOffer>(it) }.getOrNull() }
    val carriers = selectedOffer?.let { "${it.outboundAirline} · ${it.returnAirline}" }
        ?: result?.airlinesJson?.let { raw -> runCatching { Json.decodeFromString<List<String>>(raw).take(2).joinToString(" · ") }.getOrNull() }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(alert.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${alert.originIata} ↔ ${alert.destinationIata} · ${formatAlertDates(alert.outboundDate, alert.returnDate)}", color = MaterialTheme.colorScheme.secondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = active, onCheckedChange = onToggle, enabled = !isRunning && !expired, modifier = Modifier.testTag("toggle-alert-${alert.id}").sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                    Text(when { expired -> "Vencida"; active -> "Activa"; else -> "Pausada" }, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (result?.lastVerifiedTotalCop != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(formatCop(result.lastVerifiedTotalCop), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Surface(
                        color = if (current) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(if (current) "✓  Verificado" else "Precio anterior", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = if (current) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(if (current) "COP · ida y vuelta · 1 adulto" else "Último total verificado; no confirmado con los criterios actuales", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(if (result == null) "Sin resultados todavía" else result.status.replace('_', ' '), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
            if (!carriers.isNullOrBlank() && result?.lastVerifiedTotalCop != null) {
                Text("✈  $carriers${selectedOffer?.let { if (it.outbound.size == 1 && it.returning.size == 1) " · Directos" else " · Con escalas" }.orEmpty()}", color = MaterialTheme.colorScheme.secondary)
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Tu máximo", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    Text(formatCop(alert.maxTotalCop), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Revisión automática", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    Text("Cada ${alert.frequencyHours} h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(result?.let { "Última revisión: ${formatShortLocal(it.lastAttemptAt)}" } ?: "Sin revisiones", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onOpenDetail, modifier = Modifier.secondaryButtonTarget()) { Text("Ver resultados  ›") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onSearch, enabled = !isRunning, modifier = Modifier.weight(1f).primaryButtonTarget()) { Text(if (isRunning) "Consultando…" else "Revisar ahora") }
                TextButton(onClick = onEdit, enabled = !isRunning, modifier = Modifier.testTag("edit-alert-${alert.id}").secondaryButtonTarget()) { Text("Editar") }
                TextButton(onClick = onDelete, enabled = !isRunning, modifier = Modifier.testTag("delete-alert-${alert.id}").secondaryButtonTarget()) { Text("Eliminar") }
            }
        }
    }
}

@Composable
fun FlightAlertEditorScreen(
    viewModel: FlightAlertsViewModel,
    alertId: String?,
    maximumLocalGets: Int,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val editorAlert = alertId?.let { target -> state.alerts.firstOrNull { it.alert.id == target }?.alert }
    val defaultDate = LocalDate.now(COLOMBIA_ZONE).plusDays(30)
    var fields by remember(alertId, editorAlert?.updatedAt) {
        mutableStateOf(editorAlert?.let {
            AlertEditorFields(it.name, it.originIata, it.destinationIata, LocalDate.parse(it.outboundDate), LocalDate.parse(it.returnDate), it.maxTotalCop.toString(), it.frequencyHours, it.directOnly)
        } ?: AlertEditorFields("", "", "", defaultDate, defaultDate.plusDays(3), "500000", 24, true))
    }
    val active = state.alerts.map { it.alert }.filter { it.enabled }.filterNot { it.id == alertId }
    val monthlyEstimate = active.sumOf { 720L / it.frequencyHours * 4L } + 720L / fields.frequency * 4L

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.secondaryButtonTarget()) { Text("‹") }
            Text(if (alertId == null) "Nueva alerta" else "Editar alerta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Ruta y fechas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(fields.name, { fields = fields.copy(name = it.take(80)) }, label = { Text("Nombre") }, placeholder = { Text("Cartagena ↔ Cali") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("alert-name"))
                    AirportAutocompleteField(
                        "Origen", fields.origin, fields.originQuery, viewModel.airportCatalog, "alert-origin",
                        onQueryChange = { fields = fields.copy(origin = "", originQuery = it) },
                        onSelect = { fields = fields.copy(origin = it.iata, originQuery = "") },
                    )
                    AirportAutocompleteField(
                        "Destino", fields.destination, fields.destinationQuery, viewModel.airportCatalog, "alert-destination",
                        onQueryChange = { fields = fields.copy(destination = "", destinationQuery = it) },
                        onSelect = { fields = fields.copy(destination = it.iata, destinationQuery = "") },
                    )
                    Text("Solo aeropuertos incluidos en el catálogo nacional local.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateEditorButton("Ida", fields.outbound, LocalDate.now(COLOMBIA_ZONE), Modifier.weight(1f)) { selected ->
                            val nextReturn = if (fields.returning.isAfter(selected)) fields.returning else selected.plusDays(1)
                            fields = fields.copy(outbound = selected, returning = nextReturn)
                            Handler(Looper.getMainLooper()).post {
                                openDatePicker(context, nextReturn, selected.plusDays(1)) { returning ->
                                    fields = fields.copy(returning = returning)
                                }
                            }
                        }
                        DateEditorButton("Regreso", fields.returning, fields.outbound.plusDays(1), Modifier.weight(1f)) { fields = fields.copy(returning = it) }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Precio y frecuencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        fields.maximum,
                        { fields = fields.copy(maximum = it.filter(Char::isDigit).take(12)) },
                        label = { Text("Máximo total") },
                        trailingIcon = { Text("COP", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(end = 12.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("alert-maximum"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text("Ida y vuelta · 1 adulto · Económica", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { fields = fields.copy(direct = !fields.direct) }) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Solo vuelos directos", fontWeight = FontWeight.SemiBold)
                            Text("Desactívalo para permitir escalas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(checked = fields.direct, onCheckedChange = { fields = fields.copy(direct = it) }, modifier = Modifier.testTag("alert-direct-only").sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                    }
                    Text("Revisar cada", color = MaterialTheme.colorScheme.secondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(6, 8, 12, 24).forEach { frequency ->
                            FilterChip(
                                selected = fields.frequency == frequency,
                                onClick = { fields = fields.copy(frequency = frequency) },
                                label = { Text("$frequency h") },
                                modifier = Modifier.weight(1f).chipTarget(),
                            )
                        }
                    }
                    Text("Estimación: hasta $monthlyEstimate consultas al mes para las alertas activas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Text("Todas las aerolíneas que devuelva la fuente. No se inicia ninguna búsqueda al guardar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("alert-form-error")) }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(
                onClick = {
                    val maximum = fields.maximum.toLongOrNull() ?: 0L
                    viewModel.save(AlertInput(alertId, fields.name, fields.origin, fields.destination, fields.outbound, fields.returning, maximum, fields.frequency, fields.direct), onSaved)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("save-alert").primaryButtonTarget(),
            ) { Text(if (alertId == null) "Guardar alerta activa" else "Guardar cambios") }
            Text("La alerta quedará activa; Android puede aplazar sus revisiones automáticas.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun FlightAlertDetailScreen(
    viewModel: FlightAlertsViewModel,
    alertId: String?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onShowCatalog: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val item = state.alerts.firstOrNull { it.alert.id == alertId }
    val alert = item?.alert
    val result = item?.result
    var showTechnical by remember(result?.lastAttemptAt) { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.secondaryButtonTarget()) { Text("‹") }
            Text(alert?.name ?: "Detalle de alerta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (alert != null) TextButton(onClick = { onEdit(alert.id) }, modifier = Modifier.secondaryButtonTarget()) { Text("Editar") }
        }
        if (alert == null) {
            Text("No se encontró esta alerta.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.secondary)
        } else {
            val current = result?.let { isCurrentVerification(alert, it) } == true
            val offers = result?.verifiedOffersJson
                ?.let { runCatching { Json.decodeFromString<List<VerifiedRoundTripOffer>>(it) }.getOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: result?.selectedOfferJson?.let { raw -> runCatching { listOf(Json.decodeFromString<VerifiedRoundTripOffer>(raw)) }.getOrNull() }.orEmpty()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${formatAlertDates(alert.outboundDate, alert.returnDate)} · 1 adulto · Económica", color = MaterialTheme.colorScheme.secondary)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (current) "Tarifa más baja observada" else "Último resultado", color = MaterialTheme.colorScheme.secondary)
                        if (result?.lastVerifiedTotalCop != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(formatCop(result.lastVerifiedTotalCop), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Surface(color = if (current) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                    Text(if (current) "✓ Verificado" else "Dato anterior", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = if (current) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Text("COP · ida y vuelta", color = MaterialTheme.colorScheme.secondary)
                            val withinMaximum = current && result.lastVerifiedTotalCop <= alert.maxTotalCop
                            Text(
                                when {
                                    withinMaximum -> "Dentro de tu máximo de ${formatCop(alert.maxTotalCop)}"
                                    current -> "Supera tu máximo de ${formatCop(alert.maxTotalCop)}"
                                    else -> "No confirmado con los criterios actuales."
                                },
                                color = when {
                                    withinMaximum -> MaterialTheme.colorScheme.tertiary
                                    current -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.secondary
                                },
                            )
                        } else {
                            Text(result?.status?.replace('_', ' ') ?: "Aún no hay resultados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("La app mostrará aquí un total solo cuando pueda verificar la ida y el regreso asociados.", color = MaterialTheme.colorScheme.secondary)
                        }
                        HorizontalDivider()
                        if (offers.isNotEmpty()) {
                            val best = offers.minByOrNull { it.totalCop }!!
                            Text("Ida", fontWeight = FontWeight.SemiBold)
                            Text("${best.outboundAirline} · ${if (best.outbound.size <= 1) "Directo" else "${best.outbound.size - 1} escala(s)"}", color = MaterialTheme.colorScheme.secondary)
                            HorizontalDivider()
                            Text("Regreso", fontWeight = FontWeight.SemiBold)
                            Text("${best.returnAirline} · ${if (best.returning.size <= 1) "Directo" else "${best.returning.size - 1} escala(s)"}", color = MaterialTheme.colorScheme.secondary)
                        }
                        result?.let { Text("Consultado ${formatShortLocal(it.lastAttemptAt)}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (result?.explorationLimited == true) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Exploración parcial", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text("No se comprobaron todas las combinaciones.", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                if (offers.size > 1) {
                    Text("Otras tarifas verificadas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            offers.sortedBy { it.totalCop }.drop(1).forEachIndexed { index, offer ->
                                if (index > 0) HorizontalDivider()
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("${offer.outboundAirline} / ${offer.returnAirline}", modifier = Modifier.weight(1f))
                                    Text(formatCop(offer.totalCop), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Text("Totales de ida y vuelta", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                TextButton(onClick = { showTechnical = !showTechnical }, modifier = Modifier.secondaryButtonTarget()) { Text(if (showTechnical) "Ocultar información técnica" else "Ver información técnica") }
                if (showTechnical && result != null) AlertResultDetails(alert, result, onShowCatalog)
                Text("Precio observado. Verifica condiciones y disponibilidad antes de comprar.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                result?.googleFlightsUrl?.let { url ->
                    Button(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }, modifier = Modifier.fillMaxWidth().primaryButtonTarget()) { Text("Abrir Google Flights") }
                }
                OutlinedButton(onClick = { viewModel.requestReview(alert.id) }, enabled = state.runningAlertId == null, modifier = Modifier.fillMaxWidth().primaryButtonTarget()) { Text(if (state.runningAlertId == alert.id) "Consultando…" else "Revisar ahora") }
                Text("Consume hasta 4 consultas de vuelos.", modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatAlertDates(outbound: String, returning: String): String {
    val start = LocalDate.parse(outbound)
    val end = LocalDate.parse(returning)
    val locale = Locale.forLanguageTag("es-CO")
    return if (start.month == end.month && start.year == end.year) {
        "${start.dayOfMonth}–${DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(end)}"
    } else {
        "${DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(start)}–${DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(end)}"
    }
}

private fun formatShortLocal(epochMillis: Long): String = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("es-CO"))
    .withZone(COLOMBIA_ZONE).format(Instant.ofEpochMilli(epochMillis))

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
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onExpand).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(alert.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(criteria, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = alert.enabled && !expired, onCheckedChange = onToggle, enabled = !isRunning && !expired, modifier = Modifier.testTag("toggle-alert-${alert.id}").sizeIn(minWidth = 48.dp, minHeight = 48.dp))
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
            OutlinedButton(onClick = onSearch, enabled = !isRunning, modifier = Modifier.primaryButtonTarget()) { Text(if (isRunning) "Consultando…" else "Buscar") }
            TextButton(onClick = onEdit, enabled = !isRunning, modifier = Modifier.testTag("edit-alert-${alert.id}").secondaryButtonTarget()) { Text("Editar") }
            TextButton(onClick = onDelete, enabled = !isRunning, modifier = Modifier.testTag("delete-alert-${alert.id}").secondaryButtonTarget()) { Text("Eliminar") }
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
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }, modifier = Modifier.primaryButtonTarget()) {
                Text("Abrir Google Flights para verificar")
            }
        }
        OutlinedButton(onClick = onShowCatalog, modifier = Modifier.primaryButtonTarget()) { Text("Catálogo informativo de aerolíneas") }
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
        openDatePicker(context, date, minimum, onSelect)
    }, modifier = modifier.primaryButtonTarget()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("▣", color = MaterialTheme.colorScheme.secondary)
            Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO")).format(date), style = MaterialTheme.typography.bodyMedium)
            }
            Text("⌄", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

private fun openDatePicker(context: Context, date: LocalDate, minimum: LocalDate, onSelect: (LocalDate) -> Unit) {
    val start = if (date.isBefore(minimum)) minimum else date
    val dialog = DatePickerDialog(
        context,
        { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day)) },
        start.year,
        start.monthValue - 1,
        start.dayOfMonth,
    )
    dialog.datePicker.minDate = minimum.atStartOfDay(COLOMBIA_ZONE).toInstant().toEpochMilli()
    dialog.show()
}

@Composable
private fun FrequencyField(value: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.primaryButtonTarget()) { Text("Frecuencia automática: cada $value h") }
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
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.secondaryButtonTarget()) { Text("Cerrar") } },
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
