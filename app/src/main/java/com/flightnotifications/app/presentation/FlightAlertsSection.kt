package com.flightnotifications.app.presentation

import android.Manifest
import android.app.TimePickerDialog
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.repository.AlertWithResult
import com.flightnotifications.app.domain.validation.ColombianAirport
import com.flightnotifications.app.domain.validation.ColombianAirportCatalog
import com.flightnotifications.app.domain.model.VerifiedRoundTripOffer
import com.flightnotifications.app.ui.theme.FlightAppColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.text.NumberFormat
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.launch

private data class AlertEditorFields(
    val name: String,
    val origin: String,
    val destination: String,
    val outbound: LocalDate,
    val returning: LocalDate,
    val maximum: String,
    val frequency: Int,
    val direct: Boolean,
    val startTime: LocalTime = defaultStartTime(),
    val originQuery: String = "",
    val destinationQuery: String = "",
)

private object CopIntegerVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit)
        val formatted = digits.reversed().chunked(3).joinToString(".").reversed()
        val separatorsAfterDigits = (1 until digits.length).filter { (digits.length - it) % 3 == 0 }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, digits.length)
                return safeOffset + separatorsAfterDigits.count { it <= safeOffset }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, formatted.length)
                return formatted.take(safeOffset).count(Char::isDigit).coerceIn(0, digits.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

private fun airportLabel(airport: ColombianAirport): String = "${airport.city.lowercase(Locale.forLanguageTag("es-CO")).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-CO")) }} · ${airport.iata}"

private fun airportFullName(code: String, catalog: ColombianAirportCatalog): String {
    val airport = catalog.find(code) ?: return code
    val locale = Locale.forLanguageTag("es-CO")
    val name = airport.name.trim().split(Regex("\\s+")).joinToString(" ") { word ->
        word.lowercase(locale).replaceFirstChar { it.titlecase(locale) }
    }
    return "$name (${airport.iata})"
}

internal fun airportShortName(airport: ColombianAirport): String =
    Regex("\\s[-–—]\\s").find(airport.name)?.let { airport.name.substring(it.range.last + 1).trim() } ?: airport.name

private fun airportDisplayCity(airport: ColombianAirport): String {
    val separator = Regex("\\s[-–—]\\s").find(airport.name)
    val city = separator?.let { airport.name.substring(0, it.range.first).trim() } ?: airport.city
    val locale = Locale.forLanguageTag("es-CO")
    return city.lowercase(locale).replaceFirstChar { it.titlecase(locale) }
}

private fun normalizeAirportQuery(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase(Locale.ROOT)
    .trim()

private fun airportSuggestions(query: String, catalog: ColombianAirportCatalog): List<ColombianAirport> {
    val normalized = normalizeAirportQuery(query)
    if (normalized.isBlank()) {
        val popular = listOf("BOG", "MDE", "CTG", "CLO", "BAQ", "SMR", "ADZ", "PEI")
        return popular.mapNotNull(catalog::find)
    }
    return catalog.airports
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

@Composable
private fun AirportSuggestionList(
    query: String,
    catalog: ColombianAirportCatalog,
    onSelect: (ColombianAirport) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = remember(query, catalog.airports) { airportSuggestions(query, catalog) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.heightIn(max = 224.dp).verticalScroll(rememberScrollState()).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (suggestions.isEmpty()) {
                Text(
                    "No encontramos coincidencias. Prueba con otra ciudad o código IATA.",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                suggestions.forEach { airport ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onSelect(airport) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(AppIcon.Flight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${airport.city} · ${airport.iata}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(airportShortName(airport), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AirportAutocompleteField(
    label: String,
    selectedCode: String,
    query: String,
    catalog: ColombianAirportCatalog,
    tag: String,
    onQueryChange: (String) -> Unit,
    onSelect: (ColombianAirport) -> Unit,
    modifier: Modifier = Modifier,
    showSuggestionsInParent: Boolean = false,
    onFieldFocus: ((Boolean) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var editing by remember(selectedCode) { mutableStateOf(selectedCode.isBlank()) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val fieldFocusRequester = remember { FocusRequester() }
    val suggestionBringIntoView = remember { BringIntoViewRequester() }
    val selectedAirport = catalog.find(selectedCode)
    val suggestions = remember(query, catalog.airports) { airportSuggestions(query, catalog) }

    Column(modifier.fillMaxWidth()) {
        if (selectedAirport != null && query.isBlank() && !editing) {
            val accent = if (label == "Destino") Color(0xFFDB8C22) else Color(0xFF128F9B)
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable {
                    editing = true
                    expanded = true
                    onFieldFocus?.invoke(true)
                    scope.launch {
                        androidx.compose.runtime.withFrameNanos { }
                        fieldFocusRequester.requestFocus()
                        suggestionBringIntoView.bringIntoView()
                    }
                },
                color = if (label == "Destino") Color(0xFFFFF5E7) else FlightAppColors.paleBlue,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.padding(start = 6.dp, end = 0.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(color = accent.copy(alpha = 0.12f), shape = CircleShape, modifier = Modifier.size(20.dp)) {
                        Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Flight, null, modifier = Modifier.size(13.dp), tint = accent) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, color = accent, style = MaterialTheme.typography.labelSmall)
                        Text("${airportDisplayCity(selectedAirport)} (${selectedAirport.iata})", style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp, lineHeight = 11.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(airportShortName(selectedAirport), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    IconButton(
                        onClick = {
                            onQueryChange("")
                            editing = true
                            expanded = true
                            onFieldFocus?.invoke(true)
                            scope.launch {
                                androidx.compose.runtime.withFrameNanos { }
                                fieldFocusRequester.requestFocus()
                                suggestionBringIntoView.bringIntoView()
                            }
                        },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Text("×", color = accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { typed -> onQueryChange(typed); expanded = true },
                label = { Text(label) },
                placeholder = { Text("Ciudad, aeropuerto o IATA") },
                leadingIcon = { AppIcon(AppIcon.Flight, null, tint = MaterialTheme.colorScheme.secondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(fieldFocusRequester).onFocusChanged { focusState ->
                    expanded = focusState.isFocused
                    onFieldFocus?.invoke(focusState.isFocused)
                    if (focusState.isFocused) {
                        scope.launch {
                            androidx.compose.runtime.withFrameNanos { }
                            suggestionBringIntoView.bringIntoView()
                        }
                    }
                }.testTag(tag),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        }
        if (expanded && !showSuggestionsInParent) {
            Surface(
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(suggestionBringIntoView),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.heightIn(max = 248.dp).verticalScroll(rememberScrollState()).padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (suggestions.isEmpty()) {
                        Text(
                            "No encontramos coincidencias. Prueba con otra ciudad o código IATA.",
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        suggestions.forEach { airport ->
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                                    onSelect(airport)
                                    expanded = false
                                    focusManager.clearFocus()
                                }.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppIcon(AppIcon.Flight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${airport.city} · ${airport.iata}", fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(airportShortName(airport), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
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
                AlertEditorFields(it.name, it.originIata, it.destinationIata, LocalDate.parse(it.outboundDate), LocalDate.parse(it.returnDate), it.maxTotalCop.toString(), it.frequencyHours, it.directOnly, initialStartTime(it))
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
                        airportCatalog = viewModel.airportCatalog,
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
                        placeholder = { Text("500.000") },
                        prefix = { Text("\$", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary) },
                        trailingIcon = { Text("COP", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(end = 12.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("alert-maximum"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = CopIntegerVisualTransformation,
                    )
                    FrequencyField(fields.frequency, { fields = fields.copy(frequency = it) })
                    StartTimeEditorButton(fields.startTime, Modifier.fillMaxWidth()) { fields = fields.copy(startTime = it) }
                    Text("Primera revisión a esta hora; luego se intentará con la frecuencia elegida.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
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
                    val input = AlertInput(editorId, fields.name, fields.origin, fields.destination, fields.outbound, fields.returning, maximum, fields.frequency, fields.direct, fields.startTime)
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

@Composable
fun FlightAlertsSection(
    viewModel: FlightAlertsViewModel,
    maximumLocalGets: Int,
    onCreateAlert: () -> Unit,
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
    var filter by remember { mutableStateOf(AlertListFilter.ACTIVE) }
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
            AlertListFilter.INACTIVE -> !active
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Mis alertas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("$activeCount activas · ${state.alerts.size} guardadas", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.titleMedium)
            }
            Surface(
                modifier = Modifier.size(56.dp).clickable(onClick = onCreateAlert).testTag("new-alert"),
                color = Color(0xFF13C88A),
                shape = CircleShape,
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppIcon(AppIcon.Add, "Crear nueva alerta", modifier = Modifier.size(30.dp), tint = Color.White)
                }
            }
        }
        AlertStatusFilters(
            selected = filter,
            activeCount = activeCount,
            inactiveCount = state.alerts.size - activeCount,
            totalCount = state.alerts.size,
            onSelect = { filter = it },
        )

        if (activeCount > 0 && !notificationsAllowed) {
            Surface(color = FlightAppColors.warningContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(AppIcon.Notifications, null, tint = FlightAppColors.onWarningContainer)
                        Text("Android tiene desactivadas las notificaciones. Las revisiones automáticas están suspendidas para no consumir cuota.", color = FlightAppColors.onWarningContainer)
                    }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }, modifier = Modifier.fillMaxWidth().primaryButtonTarget()) { Text("Abrir ajustes de notificaciones") }
                }
            }
        }

        if (visibleAlerts.isEmpty()) {
            Card(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
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
                    airportCatalog = viewModel.airportCatalog,
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
            Surface(color = FlightAppColors.warningContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    AppIcon(AppIcon.Info, null, modifier = Modifier.size(18.dp), tint = FlightAppColors.onWarningContainer)
                    Text("Estimación automática: $monthlyLow–$monthlyHigh consultas de vuelos por 30 días. Algunas revisiones podrían esperar capacidad del presupuesto local ($maximumLocalGets).", color = FlightAppColors.onWarningContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        state.message?.let {
            FeedbackBanner(it, isError = false, modifier = Modifier.testTag("alert-feedback-message"))
        }
        state.error?.let { FeedbackBanner(it, isError = true) }
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
private fun FeedbackBanner(message: String, isError: Boolean, modifier: Modifier = Modifier) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(modifier = modifier.fillMaxWidth(), color = container, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(AppIcon.Info, null, tint = content)
            Text(message, color = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun SketchAlertCard(
    item: AlertWithResult,
    airportCatalog: ColombianAirportCatalog,
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
    val selectedOffer = result?.selectedOfferJson?.let { runCatching { Json.decodeFromString<VerifiedRoundTripOffer>(it) }.getOrNull() }
    val carriers = (selectedOffer?.let { listOf(it.outboundAirline, it.returnAirline) }
        ?: result?.airlinesJson?.let { raw -> runCatching { Json.decodeFromString<List<String>>(raw) }.getOrNull() }
        ?: emptyList())
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(Locale.ROOT) }
    var menuExpanded by remember(alert.id) { mutableStateOf(false) }
    val days = runCatching {
        java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(alert.outboundDate), LocalDate.parse(alert.returnDate)) + 1
    }.getOrDefault(0L)
    Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(color = FlightAppColors.paleBlue, shape = CircleShape, modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Flight, null, modifier = Modifier.size(21.dp), tint = Color(0xFF1767C9)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${alert.originIata} → ${alert.destinationIata}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        airportFullName(alert.originIata, airportCatalog),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        airportFullName(alert.destinationIata, airportCatalog),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = active, onCheckedChange = onToggle, enabled = !isRunning && !expired, modifier = Modifier.testTag("toggle-alert-${alert.id}").sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 48.dp)) {
                        AppIcon(AppIcon.More, "Más opciones", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Editar alerta") },
                            leadingIcon = { AppIcon(AppIcon.Edit, null, tint = MaterialTheme.colorScheme.secondary) },
                            onClick = { menuExpanded = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar alerta") },
                            leadingIcon = { AppIcon(AppIcon.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = Color(0xFFF1EBFD), shape = CircleShape, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Calendar, null, modifier = Modifier.size(19.dp), tint = Color(0xFF6D42C6)) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(formatAlertDates(alert.outboundDate, alert.returnDate), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("$days días · 1 adulto", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (result?.lastVerifiedTotalCop != null) {
                        if (carriers.isNotEmpty()) AirlineIdentity(carriers.joinToString(" / "), Modifier.fillMaxWidth(), compact = true, prominent = true)
                        else Text("Tarifa de ida y vuelta", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
                        Text(
                            when {
                                !current -> "Última tarifa observada · requiere nueva verificación"
                                selectedOffer?.let { it.outbound.size == 1 && it.returning.size == 1 } == true -> "Vuelos directos"
                                selectedOffer != null -> "Escalas permitidas"
                                else -> "COP · ida y vuelta · 1 adulto"
                            },
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                result?.lastVerifiedTotalCop?.let { price ->
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(formatCop(price), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("COP", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(
                            color = if (current) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                AppIcon(if (current) AppIcon.Verified else AppIcon.Info, null, modifier = Modifier.size(16.dp), tint = if (current) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                                Text(if (current) "Verificado" else "Anterior", color = if (current) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            if (result?.lastVerifiedTotalCop == null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppIcon(AppIcon.Info, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(if (result == null) "Aún no hay una tarifa verificada" else result.status.replace('_', ' '), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(Modifier.weight(1f), color = FlightAppColors.mintHeader, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Surface(color = FlightAppColors.mintIcon, shape = CircleShape, modifier = Modifier.size(30.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("$", color = FlightAppColors.tealDark, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        }
                        Column {
                            Text("Tu máximo", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                            Text(formatCop(alert.maxTotalCop), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Surface(Modifier.weight(1f), color = FlightAppColors.paleYellow, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Surface(color = Color(0xFFFFE6A8), shape = CircleShape, modifier = Modifier.size(30.dp)) {
                            Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Clock, null, modifier = Modifier.size(19.dp), tint = Color(0xFF936A10)) }
                        }
                        Column {
                            Text("Revisión automática", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                            Text("Cada ${alert.frequencyHours} h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppIcon(AppIcon.Refresh, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(result?.let { "Última revisión: ${formatShortLocal(it.lastAttemptAt)}" } ?: "Sin revisiones", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onSearch, enabled = !isRunning, modifier = Modifier.weight(1f).primaryButtonTarget(), shape = CircleShape) {
                    AppIcon(AppIcon.Refresh, null, modifier = Modifier.size(18.dp))
                    Text(if (isRunning) "Buscando…" else "Revisar")
                }
                Button(onClick = onOpenDetail, modifier = Modifier.weight(1f).primaryButtonTarget(), shape = CircleShape) {
                    Text("Ver resultados")
                    AppIcon(AppIcon.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
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
            AlertEditorFields(it.name, it.originIata, it.destinationIata, LocalDate.parse(it.outboundDate), LocalDate.parse(it.returnDate), it.maxTotalCop.toString(), it.frequencyHours, it.directOnly, initialStartTime(it))
        } ?: AlertEditorFields("", "", "", defaultDate, defaultDate.plusDays(3), "500000", 24, true, defaultStartTime()))
    }
    var activeAirportField by remember(alertId) { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(FlightAppColors.flightBlue, FlightAppColors.flightBlueDark)))) {
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            FlightSkyDecoration(Modifier.fillMaxSize(), hero = true)
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackIconButton(onBack)
                Text(if (alertId == null) "Nueva alerta" else "Editar alerta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column {
                    SketchSectionHeader("1. Ruta y fechas", AppIcon.Location)
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            AirportAutocompleteField(
                                "Origen", fields.origin, fields.originQuery, viewModel.airportCatalog, "alert-origin",
                                onQueryChange = { activeAirportField = "origin"; fields = fields.copy(origin = "", originQuery = it) },
                                onSelect = { activeAirportField = null; fields = fields.copy(origin = it.iata, originQuery = "") },
                                modifier = Modifier.weight(1f),
                                showSuggestionsInParent = true,
                                onFieldFocus = { focused ->
                                    if (focused) activeAirportField = "origin"
                                    else if (activeAirportField == "origin") activeAirportField = null
                                },
                            )
                            AirportAutocompleteField(
                                "Destino", fields.destination, fields.destinationQuery, viewModel.airportCatalog, "alert-destination",
                                onQueryChange = { activeAirportField = "destination"; fields = fields.copy(destination = "", destinationQuery = it) },
                                onSelect = { activeAirportField = null; fields = fields.copy(destination = it.iata, destinationQuery = "") },
                                modifier = Modifier.weight(1f),
                                showSuggestionsInParent = true,
                                onFieldFocus = { focused ->
                                    if (focused) activeAirportField = "destination"
                                    else if (activeAirportField == "destination") activeAirportField = null
                                },
                            )
                        }
                        activeAirportField?.let { activeField ->
                            val query = if (activeField == "origin") fields.originQuery else fields.destinationQuery
                            AirportSuggestionList(
                                query = query,
                                catalog = viewModel.airportCatalog,
                                onSelect = { airport ->
                                    activeAirportField = null
                                    fields = if (activeField == "origin") {
                                        fields.copy(origin = airport.iata, originQuery = "")
                                    } else {
                                        fields.copy(destination = airport.iata, destinationQuery = "")
                                    }
                                },
                            )
                        }
                        if (fields.origin.isBlank() && fields.destination.isBlank() && fields.originQuery.isBlank() && fields.destinationQuery.isBlank()) {
                            Text("Busca por ciudad, aeropuerto o código IATA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateEditorButton("Fecha de ida", fields.outbound, LocalDate.now(COLOMBIA_ZONE), Modifier.weight(1f).testTag("alert-date-outbound")) { selected ->
                                val nextReturn = if (fields.returning.isAfter(selected)) fields.returning else selected.plusDays(1)
                                fields = fields.copy(outbound = selected, returning = nextReturn)
                                Handler(Looper.getMainLooper()).post {
                                    openDatePicker(context, nextReturn, selected.plusDays(1)) { returning ->
                                        fields = fields.copy(returning = returning)
                                    }
                                }
                            }
                            DateEditorButton("Fecha de regreso", fields.returning, fields.outbound.plusDays(1), Modifier.weight(1f).testTag("alert-date-return")) { fields = fields.copy(returning = it) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                            Surface(color = FlightAppColors.mintHeader, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Flight, null, modifier = Modifier.size(22.dp), tint = FlightAppColors.tealDark) }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Solo vuelos directos", fontWeight = FontWeight.Bold)
                                Text("Sin escalas entre origen y destino.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            Switch(checked = fields.direct, onCheckedChange = { fields = fields.copy(direct = it) }, modifier = Modifier.testTag("alert-direct-only").sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                        }
                    }
                }
            }
            Card(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column {
                    SketchSectionHeader("2. Precio y frecuencia", AppIcon.Money)
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Define tu presupuesto y la programación de revisiones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        OutlinedTextField(
                        fields.maximum,
                        { fields = fields.copy(maximum = it.filter(Char::isDigit).take(12)) },
                        label = { Text("Máximo total (ida y vuelta)") },
                        placeholder = { Text("500.000") },
                        prefix = { Text("\$", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary) },
                        trailingIcon = { Text("COP", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(end = 12.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(0.70f).testTag("alert-maximum"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = CopIntegerVisualTransformation,
                    )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            StartTimeEditorButton(fields.startTime, Modifier.weight(1f)) { fields = fields.copy(startTime = it) }
                            FrequencyField(fields.frequency, { fields = fields.copy(frequency = it) }, Modifier.weight(1f))
                        }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("alert-form-error")) }
                        }
                    }
            }
            Column(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = {
                        val maximum = fields.maximum.toLongOrNull() ?: 0L
                        val originName = viewModel.airportCatalog.find(fields.origin)?.city ?: fields.origin
                        val destinationName = viewModel.airportCatalog.find(fields.destination)?.city ?: fields.destination
                        val generatedName = "$originName a $destinationName"
                        viewModel.save(AlertInput(alertId, generatedName, fields.origin, fields.destination, fields.outbound, fields.returning, maximum, fields.frequency, fields.direct, fields.startTime), onSaved)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save-alert").primaryButtonTarget(),
                    shape = CircleShape,
                ) {
                    AppIcon(AppIcon.Notifications, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Text(if (alertId == null) "Guardar alerta" else "Guardar cambios")
                }
                Text("La alerta se guardará activa. Puedes pausarla cuando quieras.", color = Color.White.copy(alpha = 0.83f), style = MaterialTheme.typography.bodySmall)
            }
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
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(FlightAppColors.flightBlue, FlightAppColors.flightBlueDark)))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackIconButton(onBack)
            Text(alert?.name?.substringBefore(" — ") ?: "Detalle de alerta", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (alert != null) TextButton(onClick = { onEdit(alert.id) }, modifier = Modifier.secondaryButtonTarget(), colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                AppIcon(AppIcon.Edit, null, tint = Color.White)
                Text("Editar")
            }
        }
        if (alert == null) {
            Text("No se encontró esta alerta.", modifier = Modifier.padding(20.dp), color = Color.White)
        } else {
            val current = result?.let { isCurrentVerification(alert, it) } == true
            val offers = result?.verifiedOffersJson
                ?.let { runCatching { Json.decodeFromString<List<VerifiedRoundTripOffer>>(it) }.getOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: result?.selectedOfferJson?.let { raw -> runCatching { listOf(Json.decodeFromString<VerifiedRoundTripOffer>(raw)) }.getOrNull() }.orEmpty()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${formatAlertDates(alert.outboundDate, alert.returnDate)} · 1 adulto · Económica", color = Color.White.copy(alpha = 0.84f), fontWeight = FontWeight.Medium)
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    Column {
                        SketchSectionHeader(if (current) "Tarifa más baja observada" else "Último resultado", AppIcon.Flight)
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Total de ida y vuelta", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                        if (result?.lastVerifiedTotalCop != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(formatCop(result.lastVerifiedTotalCop), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Surface(color = if (current) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        if (current) AppIcon(AppIcon.Verified, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                        Text(if (current) "Verificado" else "Dato anterior", color = if (current) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                            Text("COP · ida y vuelta", color = MaterialTheme.colorScheme.secondary)
                            val withinMaximum = current && result.lastVerifiedTotalCop <= alert.maxTotalCop
                            val limitContainer = when {
                                withinMaximum -> MaterialTheme.colorScheme.tertiaryContainer
                                current -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            }
                            val limitContent = when {
                                withinMaximum -> MaterialTheme.colorScheme.onTertiaryContainer
                                current -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                            Surface(color = limitContainer, shape = MaterialTheme.shapes.small) {
                                Text(
                                    when {
                                        withinMaximum -> "Dentro de tu máximo · ${formatCop(alert.maxTotalCop)}"
                                        current -> "Supera tu máximo · ${formatCop(alert.maxTotalCop)}"
                                        else -> "Precio no confirmado con los criterios actuales"
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    color = limitContent,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        } else {
                            Text(result?.status?.replace('_', ' ') ?: "Aún no hay resultados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("La app mostrará aquí un total solo cuando pueda verificar la ida y el regreso asociados.", color = MaterialTheme.colorScheme.secondary)
                        }
                        HorizontalDivider()
                        if (offers.isNotEmpty()) {
                            val best = offers.minByOrNull { it.totalCop }!!
                            FlightLegSummary("Ida", best.outboundAirline, best.outbound.size - 1)
                            HorizontalDivider()
                            FlightLegSummary("Regreso", best.returnAirline, best.returning.size - 1)
                        }
                        result?.let { Text("Consultado ${formatShortLocal(it.lastAttemptAt)}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                if (result?.explorationLimited == true) {
                    Surface(color = FlightAppColors.mintHeader, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcon(AppIcon.Info, null, tint = FlightAppColors.onWarningContainer)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Exploración parcial", fontWeight = FontWeight.SemiBold, color = FlightAppColors.tealDark)
                                Text("No se comprobaron todas las combinaciones.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (offers.size > 1) {
                    Text("Otras tarifas verificadas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Card(
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column {
                            SketchSectionHeader("Tarifas de ida y vuelta", AppIcon.Flight)
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            offers.sortedBy { it.totalCop }.drop(1).forEachIndexed { index, offer ->
                                if (index > 0) HorizontalDivider()
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        AirlineIdentity(offer.outboundAirline, Modifier.fillMaxWidth(), compact = true)
                                        AirlineIdentity(offer.returnAirline, Modifier.fillMaxWidth(), compact = true)
                                    }
                                    Text(formatCop(offer.totalCop), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Text("Totales de ida y vuelta", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showTechnical = !showTechnical },
                    modifier = Modifier.fillMaxWidth().primaryButtonTarget(),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                ) {
                    AppIcon(if (showTechnical) AppIcon.ExpandLess else AppIcon.ExpandMore, null, tint = Color.White)
                    Text(if (showTechnical) "Ocultar información técnica" else "Ver información técnica")
                }
                if (showTechnical && result != null) Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) { AlertResultDetails(alert, result, onShowCatalog) }
                Text("Precio observado. Verifica condiciones y disponibilidad antes de comprar.", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                result?.googleFlightsUrl?.let { url ->
                    Button(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }, modifier = Modifier.fillMaxWidth().primaryButtonTarget(), shape = CircleShape) {
                        AppIcon(AppIcon.OpenExternal, null, tint = MaterialTheme.colorScheme.onPrimary)
                        Text("Abrir Google Flights")
                    }
                }
                OutlinedButton(onClick = { viewModel.requestReview(alert.id) }, enabled = state.runningAlertId == null, modifier = Modifier.fillMaxWidth().primaryButtonTarget(), shape = CircleShape, colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = FlightAppColors.tealDark)) {
                    AppIcon(AppIcon.Refresh, null)
                    Text(if (state.runningAlertId == alert.id) "Consultando…" else "Revisar ahora")
                }
                Text("Consume hasta 4 consultas de vuelos.", modifier = Modifier.align(Alignment.CenterHorizontally), color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall)
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
private fun FlightLegSummary(label: String, airline: String, stops: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        AirlineIdentity(airline, Modifier.fillMaxWidth(), compact = true)
        Text(if (stops <= 0) "Directo" else "$stops escala(s)", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AirportRouteLine(direction: String, airport: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(direction, modifier = Modifier.width(58.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(airport, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AlertSummaryCard(
    item: AlertWithResult,
    airportCatalog: ColombianAirportCatalog,
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
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onExpand).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(alert.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                AirportRouteLine("Ida", airportFullName(alert.originIata, airportCatalog))
                AirportRouteLine("Regreso", airportFullName(alert.destinationIata, airportCatalog))
                Text(formatAlertDates(alert.outboundDate, alert.returnDate), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
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
        Text(
            if (result.explorationLimited) {
                "Se revisaron ${result.candidatesConsidered} opciones de salida; la exploración fue parcial."
            } else {
                "Se revisaron ${result.candidatesConsidered} opciones de salida; la fuente no informó más candidatas."
            },
        )
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
            val verifiedCarriers = if (currentVerification) {
                verifiedOffers.flatMap { listOf(it.outboundAirline, it.returnAirline) }
            } else {
                emptyList()
            }
            val notVerified = airlines.filter { airline -> verifiedCarriers.none { it.equals(airline, ignoreCase = true) } }
            Card(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppIcon(AppIcon.Flight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Aerolíneas detectadas (${airlines.size})", fontWeight = FontWeight.SemiBold)
                    }
                    Text("Son nombres devueltos por la fuente; esto no significa que cada una tenga una tarifa verificada.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    airlines.forEach { airline ->
                        val fareVerified = verifiedCarriers.any { it.equals(airline, ignoreCase = true) }
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            AirlineIdentity(airline, Modifier.weight(1f), compact = true)
                            Surface(
                                color = if (fareVerified) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    if (fareVerified) "Tarifa verificada" else "Sin tarifa verificada",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    color = if (fareVerified) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    if (notVerified.isNotEmpty()) {
                        Surface(color = FlightAppColors.warningContainer, shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppIcon(AppIcon.Info, null, modifier = Modifier.size(18.dp), tint = FlightAppColors.onWarningContainer)
                                Text(
                                    buildString {
                                        append("No se verificó una tarifa completa de: ${notVerified.joinToString(", ")}.")
                                        if (result.explorationLimited) append(" La búsqueda fue parcial y pudo dejar combinaciones sin revisar.")
                                    },
                                    color = FlightAppColors.onWarningContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Ida · ${offer.outbound.firstOrNull()?.fromIata ?: "?"} → ${offer.outbound.lastOrNull()?.toIata ?: "?"}", modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            AirlineIdentity(offer.outboundAirline, Modifier.weight(1f), compact = true)
        }
        Text(if (outStops == 0) "Directo" else "$outStops escala(s)", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Regreso · ${offer.returning.firstOrNull()?.fromIata ?: "?"} → ${offer.returning.lastOrNull()?.toIata ?: "?"}", modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            AirlineIdentity(offer.returnAirline, Modifier.weight(1f), compact = true)
        }
        Text(if (backStops == 0) "Directo" else "$backStops escala(s)", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        Text("Total de ida y vuelta observado: ${formatCop(offer.totalCop)}. Verifica condiciones y disponibilidad antes de comprar.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DateEditorButton(label: String, date: LocalDate, minimum: LocalDate, modifier: Modifier = Modifier, onSelect: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))
    val weekday = DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("es-CO"))
        .format(date)
        .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-CO")) }
    OutlinedButton(onClick = {
        openDatePicker(context, date, minimum, onSelect)
    }, modifier = modifier.heightIn(min = 68.dp).primaryButtonTarget(), shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = FlightAppColors.paleBlue),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(30.dp)) {
                Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Calendar, null, modifier = Modifier.size(20.dp), tint = Color(0xFF2585CD)) }
            }
            Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                Text(dateFormat.format(date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(weekday, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
            }
            AppIcon(AppIcon.ExpandMore, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StartTimeEditorButton(time: LocalTime, modifier: Modifier = Modifier, onSelect: (LocalTime) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onSelect(LocalTime.of(hour, minute)) },
                time.hour,
                time.minute,
                true,
            ).show()
        },
        modifier = modifier.heightIn(min = 64.dp).testTag("alert-start-time").primaryButtonTarget(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = FlightAppColors.paleLavender),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(30.dp)) {
                Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Clock, null, modifier = Modifier.size(20.dp), tint = Color(0xFF8530C5)) }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Hora de inicio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            AppIcon(AppIcon.ExpandMore, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun FrequencyField(value: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("alert-frequency").primaryButtonTarget(),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = FlightAppColors.paleBlue),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Refresh, null, modifier = Modifier.size(20.dp), tint = Color(0xFF2482D0)) }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Revisar cada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                    Text("$value horas", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                AppIcon(AppIcon.ExpandMore, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            }
        }
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
                viewModel.airlineCatalog.airlines.forEach { AirlineIdentity("${it.name} (${it.code})", Modifier.fillMaxWidth(), compact = true) }
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

private fun defaultStartTime(): LocalTime = LocalTime.now(COLOMBIA_ZONE)
    .plusHours(1)
    .withMinute(0)
    .withSecond(0)
    .withNano(0)

private fun initialStartTime(alert: FlightAlertEntity): LocalTime = alert.nextDueAt
    ?.let { Instant.ofEpochMilli(it).atZone(COLOMBIA_ZONE).toLocalTime().withSecond(0).withNano(0) }
    ?: defaultStartTime()
