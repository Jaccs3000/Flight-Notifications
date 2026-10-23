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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.repository.AlertWithResult
import com.flightnotifications.app.domain.validation.AirportLocation
import com.flightnotifications.app.domain.validation.AirportCatalog
import com.flightnotifications.app.domain.model.VerifiedRoundTripOffer
import com.flightnotifications.app.domain.model.FlightDepartureTime
import com.flightnotifications.app.domain.model.MinorPassenger
import com.flightnotifications.app.domain.model.FlexibleFlightDates
import com.flightnotifications.app.domain.model.PassengerSelection
import com.flightnotifications.app.ui.theme.FlightAppColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.text.NumberFormat
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
    val returning: LocalDate?,
    val maximum: String,
    val frequency: Int,
    val direct: Boolean,
    val startTime: LocalTime = defaultStartTime(),
    val adults18Plus: Int = 1,
    val minors: List<MinorPassenger> = emptyList(),
    val originQuery: String = "",
    val destinationQuery: String = "",
    val flexibleDates: Boolean = false,
    val outboundEnd: LocalDate = outbound,
    val returnEnd: LocalDate? = returning,
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

private fun editorFieldsFrom(alert: FlightAlertEntity): AlertEditorFields = AlertEditorFields(
    name = alert.name,
    origin = alert.originIata,
    destination = alert.destinationIata,
    outbound = LocalDate.parse(alert.outboundDate),
    returning = alert.returnDate.takeIf(String::isNotBlank)?.let(LocalDate::parse),
    maximum = alert.maxTotalCop.toString(),
    frequency = alert.frequencyHours,
    direct = alert.directOnly,
    startTime = initialStartTime(alert),
    adults18Plus = alert.adults18Plus,
    minors = runCatching { Json.decodeFromString<List<MinorPassenger>>(alert.minorPassengersJson) }.getOrDefault(emptyList()),
    flexibleDates = alert.flexibleDateSearch,
    outboundEnd = alert.outboundDateEnd.takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: LocalDate.parse(alert.outboundDate),
    returnEnd = alert.returnDateEnd.takeIf(String::isNotBlank)?.let(LocalDate::parse)
        ?: alert.returnDate.takeIf(String::isNotBlank)?.let(LocalDate::parse),
)

private fun airportCountryName(airport: AirportLocation): String =
    Locale.Builder().setRegion(airport.country).build()
        .getDisplayCountry(Locale.forLanguageTag("es-CO")).ifBlank { airport.country }

private fun airportCityCountryName(code: String, catalog: AirportCatalog): String = catalog.find(code)?.let {
    "${it.city} (${airportCountryName(it)})"
} ?: code

private fun isAlertExpired(alert: FlightAlertEntity, catalog: AirportCatalog): Boolean {
    val zone = catalog.timeZoneFor(alert.originIata) ?: COLOMBIA_ZONE
    val finalOutboundDate = if (alert.flexibleDateSearch) alert.outboundDateEnd.ifBlank { alert.outboundDate } else alert.outboundDate
    return Instant.now().atZone(zone).toLocalDate().isAfter(LocalDate.parse(finalOutboundDate))
}

private fun airportFullName(code: String, catalog: AirportCatalog): String {
    val airport = catalog.find(code) ?: return code
    val locale = Locale.forLanguageTag("es-CO")
    val name = airport.name.trim().split(Regex("\\s+")).joinToString(" ") { word ->
        word.lowercase(locale).replaceFirstChar { it.titlecase(locale) }
    }.replace(Regex("(?i)nunez"), "Núñez")
        .replace(Regex("(?i)matecana"), "Matecaña")
        .replace(Regex("(?i)aragon"), "Aragón")
    return "$name (${airport.iata}) · ${airportCountryName(airport)}"
}

internal fun airportShortName(airport: AirportLocation): String =
    Regex("\\s[-–—]\\s").find(airport.name)?.let { airport.name.substring(it.range.last + 1).trim() } ?: airport.name

private fun airportDisplayCity(airport: AirportLocation): String {
    val separator = Regex("\\s[-–—]\\s").find(airport.name)
    val city = separator?.let { airport.name.substring(0, it.range.first).trim() } ?: airport.city
    val locale = Locale.forLanguageTag("es-CO")
    return city.lowercase(locale).replaceFirstChar { it.titlecase(locale) }
}

private fun airportSuggestions(query: String, catalog: AirportCatalog): List<AirportLocation> = catalog.suggestions(query)

@Composable
private fun AirportSuggestionList(
    query: String,
    catalog: AirportCatalog,
    onSelect: (AirportLocation) -> Unit,
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
                            Text("${airportShortName(airport)} · ${airportCountryName(airport)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
    catalog: AirportCatalog,
    tag: String,
    onQueryChange: (String) -> Unit,
    onSelect: (AirportLocation) -> Unit,
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
                        Text("${airportShortName(selectedAirport)} · ${airportCountryName(selectedAirport)}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                                    Text("${airportShortName(airport)} · ${airportCountryName(airport)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
    val activeCount = state.alerts.count { it.alert.enabled && !isAlertExpired(it.alert, viewModel.airportCatalog) }
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
            editorAlert?.let(::editorFieldsFrom)
                ?: AlertEditorFields("", "", "", defaultDate, defaultDate.plusDays(3), "500000", 24, true),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Alertas de vuelos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("$activeCount activas · ${state.alerts.size} configuradas · vuelos en todo el mundo", style = MaterialTheme.typography.bodyMedium)
        }

        if (activeCount > 0 && !notificationsAllowed) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Las búsquedas automáticas continúan, pero Android tiene desactivadas las notificaciones y no recibirás avisos de nuevas tarifas.",
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
                    val expired = isAlertExpired(alert, viewModel.airportCatalog)
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
                val active = state.alerts.map { it.alert }.filter { it.enabled && !isAlertExpired(it, viewModel.airportCatalog) }
                val monthlyLow = active.sumOf { 720L / it.frequencyHours * if (it.returnDate.isBlank()) 1L else 2L }
                val monthlyHigh = active.sumOf { 720L / it.frequencyHours * if (it.returnDate.isBlank()) 1L else com.flightnotifications.app.data.repository.SerpApiRepository.MAX_GETS_PER_ALERT.toLong() }
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
                Text("Un solo coordinador periódico con red conectada. Las frecuencias son objetivos: Android puede aplazar la ejecución por ahorro de batería, Doze o falta de red. La búsqueda se ejecuta aunque los avisos estén desactivados, pero no recibirás notificaciones. Si el sistema fuerza el cierre, al volver a abrir la app se reprograma el trabajo y se intentan recuperar revisiones vencidas.", style = MaterialTheme.typography.bodySmall)
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
                    Text("Busca aeropuertos por ciudad, nombre o código IATA en cualquier país.", style = MaterialTheme.typography.bodySmall)
                    TripTypeSelector(fields.returning != null, onSelect = { roundTrip ->
                        fields = fields.copy(returning = if (roundTrip) fields.returning ?: fields.outbound.plusDays(3) else null)
                    })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateEditorButton(
                            "Ida",
                            fields.outbound,
                            LocalDate.now(viewModel.airportCatalog.timeZoneFor(fields.origin) ?: COLOMBIA_ZONE),
                            Modifier.weight(1f),
                        ) { selected ->
                            val nextReturn = fields.returning?.let { if (it.isAfter(selected)) it else selected.plusDays(1) }
                            fields = fields.copy(outbound = selected, returning = nextReturn)
                            if (nextReturn != null) Handler(Looper.getMainLooper()).post {
                                openDatePicker(context, nextReturn, selected.plusDays(1)) { returning ->
                                    fields = fields.copy(returning = returning)
                                }
                            }
                        }
                        fields.returning?.let { returning -> DateEditorButton("Regreso", returning, fields.outbound.plusDays(1), Modifier.weight(1f)) { fields = fields.copy(returning = it) } }
                    }
                    PassengerSelectionEditor(fields.adults18Plus, fields.minors, { fields = fields.copy(adults18Plus = it) }, { fields = fields.copy(minors = it) })
                    OutlinedTextField(
                        fields.maximum,
                        { fields = fields.copy(maximum = it.filter(Char::isDigit).take(12)) },
                        label = { Text(if (fields.returning == null) "Máximo total solo ida · COP" else "Máximo total ida y vuelta · COP") },
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
                    Text("Económica · COP · todas las aerolíneas. Se envían cantidades por categoría admitida; la edad exacta solo se conserva en la app.", style = MaterialTheme.typography.bodySmall)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("alert-form-error")) }
                    state.alerts.map { it.alert }.filter { it.enabled }.let { active ->
                        val existing = active.filterNot { it.id == editorId }
                        val maxRoundTripGets = com.flightnotifications.app.data.repository.SerpApiRepository.MAX_GETS_PER_ALERT.toLong()
                        val projected = existing.sumOf { 720L / it.frequencyHours * if (it.returnDate.isBlank()) 1L else maxRoundTripGets } +
                            720L / fields.frequency * if (fields.returning == null) 1L else maxRoundTripGets
                        Text("Estimación alta con esta configuración: $projected GET/30 días si se activa. Presupuesto local: $maximumLocalGets; quedará activa al guardarla.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val maximum = fields.maximum.toLongOrNull() ?: 0L
                    val input = AlertInput(editorId, fields.name, fields.origin, fields.destination, fields.outbound, fields.returning, maximum, fields.frequency, fields.direct, fields.startTime, fields.adults18Plus, fields.minors)
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
            text = { Text("${reviewAlert.originIata} ${if (reviewAlert.returnDate.isBlank()) "→" else "↔"} ${reviewAlert.destinationIata}, ${reviewAlert.outboundDate}${reviewAlert.returnDate.takeIf(String::isNotBlank)?.let { " / $it" }.orEmpty()}. ${alertPassengerSummary(reviewAlert)}. Se comprobará la cuenta una vez; ${if (reviewAlert.returnDate.isBlank()) "la búsqueda de solo ida consume 1 GET de vuelos" else "la ida y vuelta puede iniciar hasta 9 GET (1 inicial + hasta 8 regresos asociados)"}. Cancelar inicia 0 GET. La revisión manual no activa alertas pausadas.") },
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
    val orderedAlerts = remember(state.alerts, state.alertOrder) {
        val byId = state.alerts.associateBy { it.alert.id }
        orderedAlertIds(state.alerts.map { it.alert.id }, state.alertOrder).mapNotNull(byId::get)
    }
    val activeAlerts = orderedAlerts.filter {
        it.alert.enabled && !isAlertExpired(it.alert, viewModel.airportCatalog)
    }
    val activeCount = activeAlerts.size
    val visibleAlerts = orderedAlerts.filter { item ->
        val active = item.alert.enabled && !isAlertExpired(item.alert, viewModel.airportCatalog)
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
                        Text("Las búsquedas automáticas continúan, pero no recibirás notificaciones mientras estén desactivadas en Android.", color = FlightAppColors.onWarningContainer)
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
                    Text("Crea una alerta para vigilar cualquier ruta y un rango de fechas.", color = MaterialTheme.colorScheme.secondary)
                }
            }
        } else {
            val visibleIds = visibleAlerts.map { it.alert.id }
            visibleAlerts.forEachIndexed { index, item ->
                val alert = item.alert
                val expired = isAlertExpired(alert, viewModel.airportCatalog)
                SketchAlertCard(
                    item = item,
                    airportCatalog = viewModel.airportCatalog,
                    expired = expired,
                    isRunning = state.runningAlertId == alert.id,
                    isChangingEnabled = alert.id in state.pendingToggleIds,
                    onOpenDetail = { onOpenDetail(alert.id) },
                    onEdit = { onEditAlert(alert.id) },
                    onDelete = { viewModel.requestDelete(alert.id) },
                    onSearch = { viewModel.requestReview(alert.id) },
                    canMoveUp = index > 0,
                    canMoveDown = index < visibleAlerts.lastIndex,
                    onMoveUp = { viewModel.moveAlert(alert.id, visibleIds, -1) },
                    onMoveDown = { viewModel.moveAlert(alert.id, visibleIds, 1) },
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

        state.message?.takeUnless { it.startsWith("Tarifa de ") }?.let {
            FeedbackBanner(it, isError = false, modifier = Modifier.testTag("alert-feedback-message"))
        }
        state.error?.let { FeedbackBanner(it, isError = true) }
    }

    val reviewAlert = state.reviewConfirmationId?.let { id -> state.alerts.firstOrNull { it.alert.id == id }?.alert }
    if (reviewAlert != null) AlertDialog(
        onDismissRequest = viewModel::dismissReviewConfirmation,
        title = { Text("Revisar precios ahora") },
            text = { Text("${reviewAlert.originIata} ${if (reviewAlert.returnDate.isBlank()) "→" else "↔"} ${reviewAlert.destinationIata}, ${reviewAlert.outboundDate}${reviewAlert.returnDate.takeIf(String::isNotBlank)?.let { " / $it" }.orEmpty()}. ${alertPassengerSummary(reviewAlert)}. ${if (reviewAlert.returnDate.isBlank()) "La búsqueda de solo ida consume 1 GET" else "La ida y vuelta puede iniciar hasta 9 GET"}. Cancelar inicia 0 GET. La revisión manual no activa una alerta pausada.") },
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
    airportCatalog: AirportCatalog,
    expired: Boolean,
    isRunning: Boolean,
    isChangingEnabled: Boolean = false,
    onOpenDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSearch: () -> Unit,
    onToggle: (Boolean) -> Unit,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
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
    val tripDuration = alert.returnDate.takeIf { it.isNotBlank() && !alert.flexibleDateSearch }?.let { returning ->
        runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(alert.outboundDate), LocalDate.parse(returning)) + 1 }.getOrNull()
    }
    val passengerSummary = alertPassengerSummary(alert)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val valueStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val metadataMinWidth = with(density) {
        maxOf(
            textMeasurer.measure(AnnotatedString(formatCop(alert.maxTotalCop)), style = valueStyle).size.width,
            textMeasurer.measure(AnnotatedString("Cada ${alert.frequencyHours} h"), style = valueStyle).size.width,
        ).toDp()
    } + 54.dp // Icon, gap and padding; the value remains on one line.
    val actionMinWidth = with(density) {
        textMeasurer.measure(AnnotatedString("Ver resultados"), style = MaterialTheme.typography.labelLarge).size.width.toDp()
    } + 46.dp // Icon, gap and padding; both actions use the same minimum width.
    val dateText = formatAlertDates(alert, compact = true)
    val routeStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val dateStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val offerPrice = result?.lastVerifiedTotalCop
    val partialRange = alert.flexibleDateSearch && result?.explorationLimited == true
    val offerHeader = when {
        current && partialRange -> "Mejor tarifa observada hasta ahora"
        current && alert.flexibleDateSearch -> "Mejor tarifa observada en el rango"
        current -> "Tarifa más baja observada"
        else -> "Último resultado"
    }
    val outboundDepartureTime = FlightDepartureTime.format(selectedOffer?.outbound?.firstOrNull()?.departureLocal)
    val returnDepartureTime = FlightDepartureTime.format(selectedOffer?.returning?.firstOrNull()?.departureLocal)
    val offerLayout = offerPrice?.let { price ->
        val leftMinimumWidth = with(density) {
            maxOf(
                textMeasurer.measure(AnnotatedString("${alert.originIata} ${if (alert.returnDate.isBlank()) "→" else "↔"} ${alert.destinationIata}"), style = routeStyle).size.width,
                textMeasurer.measure(AnnotatedString(dateText), style = dateStyle).size.width,
            ).toDp()
        } + 38.dp // Route/date icon and gap at the left of the text column.
        val rightMinimumWidth = with(density) {
            val priceWidth = textMeasurer.measure(
                AnnotatedString(formatCop(price)),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            ).size.width.toDp() + textMeasurer.measure(AnnotatedString("COP"), style = MaterialTheme.typography.bodySmall).size.width.toDp() + 4.dp
            maxOf(priceWidth, 156.dp)
        }
        leftMinimumWidth to rightMinimumWidth
    }
    Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().testTag("alert-card-${alert.id}")) {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("alert-header-${alert.id}"),
                color = Color(0xFFDFF0FD),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val headerWidth = maxWidth
                    val compactHeader = headerWidth < 380.dp
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFFD8EDFC), Color(0xFFE6F4FD), Color(0xFFD8EDFC))))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                    if (carriers.isNotEmpty()) {
                        AirlineIdentity(
                            carriers.joinToString(" / "),
                            modifier = Modifier.weight(1f).testTag("alert-airline-${alert.id}"),
                            prominent = !compactHeader,
                            prominentCompact = compactHeader,
                            logoSize = if (compactHeader || carriers.size > 1) 32.dp else 48.dp,
                        )
                    } else {
                        Row(
                            Modifier.weight(1f).testTag("alert-airline-${alert.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(38.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    AppIcon(AppIcon.Flight, null, modifier = Modifier.size(21.dp), tint = Color(0xFF1767C9))
                                }
                            }
                            Text(
                                "Por confirmar",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                    CompactAlertSwitch(
                        checked = active,
                        onCheckedChange = onToggle,
                        enabled = !isRunning && !isChangingEnabled && !expired,
                        modifier = Modifier.testTag("toggle-alert-${alert.id}"),
                    )
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 48.dp).testTag("alert-menu-${alert.id}")) {
                            AppIcon(AppIcon.More, "Más opciones", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                modifier = Modifier.testTag("move-alert-up-${alert.id}"),
                                enabled = canMoveUp,
                                text = { Text("Mover arriba") },
                                leadingIcon = { AppIcon(AppIcon.ExpandLess, null, tint = MaterialTheme.colorScheme.secondary) },
                                onClick = { menuExpanded = false; onMoveUp() },
                            )
                            DropdownMenuItem(
                                modifier = Modifier.testTag("move-alert-down-${alert.id}"),
                                enabled = canMoveDown,
                                text = { Text("Mover abajo") },
                                leadingIcon = { AppIcon(AppIcon.ExpandMore, null, tint = MaterialTheme.colorScheme.secondary) },
                                onClick = { menuExpanded = false; onMoveDown() },
                            )
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
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val splitOffer = offerLayout != null && maxWidth >= offerLayout.first + offerLayout.second + 9.dp
                if (splitOffer) {
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AlertRouteAndDates(
                            alert = alert,
                            airportCatalog = airportCatalog,
                            dateText = dateText,
                            tripDuration = tripDuration,
                            passengerSummary = passengerSummary,
                            modifier = Modifier.weight(offerLayout.first.value),
                        )
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                        AlertOfferSummary(
                            price = offerPrice,
                            current = current,
                            explorationLimited = result?.explorationLimited == true,
                            flexibleDateSearch = alert.flexibleDateSearch,
                            datePairsChecked = result?.datePairsChecked ?: 0,
                            datePairCount = result?.datePairCount ?: 1,
                            datePairsWithIncompleteOptions = result?.datePairIncompleteMask?.let(java.lang.Long::bitCount) ?: 0,
                            outboundDepartureTime = outboundDepartureTime,
                            returnDepartureTime = returnDepartureTime,
                            outboundDepartureDate = if (alert.flexibleDateSearch) offerDepartureDate(selectedOffer?.outbound?.firstOrNull()?.departureLocal) else null,
                            returnDepartureDate = if (alert.flexibleDateSearch) offerDepartureDate(selectedOffer?.returning?.firstOrNull()?.departureLocal) else null,
                            modifier = Modifier.weight(offerLayout.second.value),
                            alertId = alert.id,
                        )
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AlertRouteAndDates(alert, airportCatalog, dateText, tripDuration, passengerSummary, Modifier.fillMaxWidth())
                        offerPrice?.let { price ->
                            AlertOfferSummary(
                                price = price,
                                current = current,
                                explorationLimited = result?.explorationLimited == true,
                                flexibleDateSearch = alert.flexibleDateSearch,
                                datePairsChecked = result?.datePairsChecked ?: 0,
                                datePairCount = result?.datePairCount ?: 1,
                                datePairsWithIncompleteOptions = result?.datePairIncompleteMask?.let(java.lang.Long::bitCount) ?: 0,
                                outboundDepartureTime = outboundDepartureTime,
                                returnDepartureTime = returnDepartureTime,
                                outboundDepartureDate = if (alert.flexibleDateSearch) offerDepartureDate(selectedOffer?.outbound?.firstOrNull()?.departureLocal) else null,
                                returnDepartureDate = if (alert.flexibleDateSearch) offerDepartureDate(selectedOffer?.returning?.firstOrNull()?.departureLocal) else null,
                                modifier = Modifier.fillMaxWidth(),
                                alertId = alert.id,
                            )
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
            AlertCardPair(metadataMinWidth, first = { itemModifier ->
                Surface(itemModifier.testTag("alert-budget-${alert.id}"), color = FlightAppColors.mintHeader, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.height(IntrinsicSize.Min).padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = FlightAppColors.mintIcon, shape = CircleShape, modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("$", color = FlightAppColors.tealDark, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                            Text("Tu máximo", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                            Text(formatCop(alert.maxTotalCop), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }, second = { itemModifier ->
                Surface(itemModifier.testTag("alert-frequency-${alert.id}"), color = FlightAppColors.paleYellow, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.height(IntrinsicSize.Min).padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = Color(0xFFFFE6A8), shape = CircleShape, modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Clock, null, modifier = Modifier.size(19.dp), tint = Color(0xFF936A10)) }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                            Text("Revisión automática", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                            Text("Cada ${alert.frequencyHours} h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            })
            val reviewSummary = result?.let { "Última revisión: ${formatShortLocal(it.lastAttemptAt)}" } ?: "Sin revisiones"
            if (density.fontScale > 1.15f) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AlertReviewMeta(reviewSummary, alert.id, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        AlertDirectCaption(alert.directOnly, alert.id)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertReviewMeta(reviewSummary, alert.id, Modifier.weight(1f))
                    AlertDirectCaption(alert.directOnly, alert.id)
                }
            }
            Box(Modifier.fillMaxWidth()) {
                val reviewButton: @Composable (Modifier) -> Unit = { buttonModifier ->
                    ReviewFlightButton(
                        modifier = buttonModifier.primaryButtonTarget().testTag("alert-review-${alert.id}"),
                        onClick = onSearch,
                        isRunning = isRunning,
                    )
                }
                val resultsButton: @Composable (Modifier) -> Unit = { buttonModifier ->
                    Button(onClick = onOpenDetail, modifier = buttonModifier.primaryButtonTarget().testTag("alert-results-${alert.id}"), shape = CircleShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                        Text("Ver resultados", modifier = Modifier.padding(end = 4.dp), maxLines = 1, softWrap = false)
                        AppIcon(AppIcon.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                AlertCardPair(actionMinWidth, first = reviewButton, second = resultsButton)
            }
            }
        }
    }
}

@Composable
private fun ReviewFlightButton(
    modifier: Modifier,
    onClick: () -> Unit,
    isRunning: Boolean,
    idleLabel: String = "Revisar",
    canStart: Boolean = !isRunning,
) {
    val phase = if (isRunning) {
        val transition = rememberInfiniteTransition(label = "flight-review-blink")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 480),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "flight-review-button-color",
        )
        animatedPhase
    } else {
        0f
    }
    val pulseColor = when {
        !isRunning -> Color(0xFF1767C9)
        phase < 0.5f -> Color(0xFFF57C00)
        else -> Color(0xFFC62828)
    }
    val pulseContentColor = when {
        !isRunning -> Color(0xFF1767C9)
        phase < 0.5f -> Color(0xFF18284D)
        else -> Color.White
    }
    OutlinedButton(
        onClick = onClick,
        enabled = canStart,
        modifier = modifier,
        shape = CircleShape,
        border = BorderStroke(if (isRunning) 2.dp else 1.dp, pulseColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFF1767C9),
            containerColor = Color.White,
            disabledContentColor = pulseContentColor,
            disabledContainerColor = pulseColor,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        AppIcon(AppIcon.Refresh, null, modifier = Modifier.size(18.dp), tint = pulseContentColor)
        Text(if (isRunning) "Buscando…" else idleLabel, modifier = Modifier.padding(start = 4.dp), color = pulseContentColor, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun AlertReviewMeta(text: String, alertId: String, modifier: Modifier = Modifier) {
    Row(
        modifier.testTag("alert-review-meta-$alertId"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIcon(AppIcon.Refresh, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        Text(
            text,
            modifier = Modifier.testTag("alert-review-meta-text-$alertId"),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlertDirectCaption(directOnly: Boolean, alertId: String) {
    val label = if (directOnly) "Vuelos directos" else "Incluye escalas"
    Row(
        Modifier.testTag("alert-direct-$alertId"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(AppIcon.Flight, label, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        Text(
            label,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlertRouteAndDates(
    alert: FlightAlertEntity,
    airportCatalog: AirportCatalog,
    dateText: String,
    tripDuration: Long?,
    passengerSummary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("alert-details-${alert.id}"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(color = FlightAppColors.paleBlue, shape = CircleShape, modifier = Modifier.size(30.dp).testTag("alert-route-icon-${alert.id}")) {
                Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Flight, null, modifier = Modifier.size(19.dp), tint = Color(0xFF1767C9)) }
            }
            Column(Modifier.weight(1f).testTag("alert-route-${alert.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${alert.originIata} ${if (alert.returnDate.isBlank()) "→" else "↔"} ${alert.destinationIata}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    airportFullName(alert.originIata, airportCatalog),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    maxLines = 2,
                )
                Text(
                    airportFullName(alert.destinationIata, airportCatalog),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    maxLines = 2,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().testTag("alert-dates-${alert.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(color = Color(0xFFF1EBFD), shape = CircleShape, modifier = Modifier.size(30.dp).testTag("alert-date-icon-${alert.id}")) {
                Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Calendar, null, modifier = Modifier.size(18.dp), tint = Color(0xFF6D42C6)) }
            }
            Column(Modifier.weight(1f).testTag("alert-date-text-${alert.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    dateText,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = if (alert.flexibleDateSearch) 13.sp else 15.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = if (alert.flexibleDateSearch) 2 else 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = true,
                )
                Text(listOfNotNull(tripDuration?.let { "$it días" }, passengerSummary).joinToString(" · "), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun AlertOfferSummary(
    price: Long,
    current: Boolean,
    explorationLimited: Boolean,
    flexibleDateSearch: Boolean,
    datePairsChecked: Int,
    datePairCount: Int,
    datePairsWithIncompleteOptions: Int,
    outboundDepartureTime: String?,
    returnDepartureTime: String?,
    outboundDepartureDate: String?,
    returnDepartureDate: String?,
    modifier: Modifier = Modifier,
    alertId: String,
) {
    Column(
        modifier = modifier.testTag("alert-offer-$alertId"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.testTag("alert-price-$alertId").semantics {
                contentDescription = if (current) "Oferta verificada, ${formatCop(price)}" else "Oferta anterior verificada, ${formatCop(price)}"
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(formatCop(price), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("COP", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
        if (current && explorationLimited) {
            val coverage = if (flexibleDateSearch) {
                buildString {
                    append("Rango parcial · ${datePairsChecked.coerceAtLeast(0)} de ${datePairCount.coerceAtLeast(1)} fechas")
                    if (datePairsWithIncompleteOptions > 0) append(" · opciones incompletas en $datePairsWithIncompleteOptions")
                }
            } else {
                "Búsqueda parcial · puede haber opciones más baratas"
            }
            Text(
                coverage,
                color = FlightAppColors.onWarningContainer,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
        if (!current) {
            Text("Horario de la última tarifa verificada", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            DepartureTimeTile("Ida", outboundDepartureTime, returning = false, Modifier.weight(1f).testTag("alert-outbound-time-$alertId"), date = outboundDepartureDate)
            DepartureTimeTile("Regreso", returnDepartureTime, returning = true, Modifier.weight(1f).testTag("alert-return-time-$alertId"), date = returnDepartureDate)
        }
    }
}

@Composable
private fun DepartureTimeTile(label: String, time: String?, returning: Boolean, modifier: Modifier = Modifier, date: String? = null) {
    val color = if (returning) Color(0xFF6D42C6) else Color(0xFF1767C9)
    Surface(
        modifier = modifier,
        color = if (returning) FlightAppColors.paleLavender else FlightAppColors.paleBlue,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), maxLines = 1, softWrap = false)
            Text(time ?: "—", color = color, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 23.sp), fontWeight = FontWeight.Bold, maxLines = 1)
            date?.let { Text(it, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
    }
}

@Composable
private fun AlertCardPair(
    minimumItemWidth: Dp,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= minimumItemWidth * 2 + 8.dp) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                first(Modifier.weight(1f).fillMaxHeight())
                second(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun CompactAlertSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) Color(0xFF008F91) else Color(0xFFD9E0E6),
        label = "alert-switch-track",
    )
    val thumbOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        label = "alert-switch-thumb",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(x = 3.dp)
                .width(42.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (enabled) trackColor else trackColor.copy(alpha = 0.55f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .offset { IntOffset(x = thumbOffset.roundToPx(), y = 0) }
                    .size(20.dp)
                    .shadow(1.dp, CircleShape)
                    .background(Color.White, CircleShape),
            ) {}
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
        mutableStateOf(editorAlert?.let(::editorFieldsFrom)
            ?: AlertEditorFields("", "", "", defaultDate, defaultDate.plusDays(3), "500000", 24, true, defaultStartTime()))
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
                        TripTypeSelector(fields.returning != null, onSelect = { roundTrip ->
                            val nextReturn = if (roundTrip) fields.returning ?: fields.outbound.plusDays(3) else null
                            fields = fields.copy(returning = nextReturn, returnEnd = if (nextReturn == null) null else fields.returnEnd ?: nextReturn)
                        })
                        DateSearchModeSelector(fields.flexibleDates) { flexible ->
                            fields = fields.copy(
                                flexibleDates = flexible,
                                outboundEnd = if (flexible) fields.outboundEnd.coerceAtLeast(fields.outbound) else fields.outbound,
                                returnEnd = fields.returning?.let { if (flexible) (fields.returnEnd ?: it).coerceAtLeast(it) else it },
                            )
                        }
                        val earliestOutbound = LocalDate.now(viewModel.airportCatalog.timeZoneFor(fields.origin) ?: COLOMBIA_ZONE)
                        if (fields.flexibleDates) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DateEditorButton("Ida desde", fields.outbound, earliestOutbound, Modifier.weight(1f).testTag("alert-date-outbound-start")) { selected ->
                                    val end = fields.outboundEnd.coerceAtLeast(selected)
                                    val nextReturn = fields.returning?.let { if (it.isAfter(selected)) it else selected.plusDays(1) }
                                    fields = fields.copy(outbound = selected, outboundEnd = end, returning = nextReturn, returnEnd = nextReturn?.let { maxOf(fields.returnEnd ?: it, it) })
                                }
                                DateEditorButton("Ida hasta", fields.outboundEnd, fields.outbound, Modifier.weight(1f).testTag("alert-date-outbound-end")) { selected ->
                                    fields = fields.copy(outboundEnd = selected)
                                }
                            }
                            fields.returning?.let { returnStart ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DateEditorButton("Regreso desde", returnStart, fields.outbound.plusDays(1), Modifier.weight(1f).testTag("alert-date-return-start")) { selected ->
                                        fields = fields.copy(returning = selected, returnEnd = maxOf(fields.returnEnd ?: selected, selected))
                                    }
                                    DateEditorButton("Regreso hasta", fields.returnEnd ?: returnStart, returnStart, Modifier.weight(1f).testTag("alert-date-return-end")) { selected ->
                                        fields = fields.copy(returnEnd = selected)
                                    }
                                }
                            }
                            val combinations = FlexibleFlightDates.allPairs(fields.outbound, fields.outboundEnd, fields.returning, fields.returnEnd).size
                            Text("Se probará una combinación por revisión automática. Combinaciones válidas en el rango: $combinations (máximo 49).", modifier = Modifier.testTag("alert-date-combination-count"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DateEditorButton(
                                    "Fecha de ida", fields.outbound, earliestOutbound, Modifier.weight(1f).testTag("alert-date-outbound"),
                                ) { selected ->
                                    val nextReturn = fields.returning?.let { if (it.isAfter(selected)) it else selected.plusDays(1) }
                                    fields = fields.copy(outbound = selected, outboundEnd = selected, returning = nextReturn, returnEnd = nextReturn)
                                    if (nextReturn != null) Handler(Looper.getMainLooper()).post {
                                        openDatePicker(context, nextReturn, selected.plusDays(1)) { returning ->
                                            fields = fields.copy(returning = returning, returnEnd = returning)
                                        }
                                    }
                                }
                                fields.returning?.let { returning -> DateEditorButton("Fecha de regreso", returning, fields.outbound.plusDays(1), Modifier.weight(1f).testTag("alert-date-return")) { fields = fields.copy(returning = it, returnEnd = it) } }
                            }
                        }
                        PassengerSelectionEditor(fields.adults18Plus, fields.minors, { fields = fields.copy(adults18Plus = it) }, { fields = fields.copy(minors = it) })
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
                        label = { Text(if (fields.returning == null) "Máximo total (solo ida)" else "Máximo total (ida y vuelta)") },
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
                        val originName = airportCityCountryName(fields.origin, viewModel.airportCatalog)
                        val destinationName = airportCityCountryName(fields.destination, viewModel.airportCatalog)
                        val generatedName = "$originName a $destinationName".take(80)
                        viewModel.save(AlertInput(alertId, generatedName, fields.origin, fields.destination, fields.outbound, fields.returning, maximum, fields.frequency, fields.direct, fields.startTime, fields.adults18Plus, fields.minors, fields.flexibleDates, fields.outboundEnd, fields.returnEnd), onSaved)
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
            val detailHeader = when {
                current && alert.flexibleDateSearch && result?.explorationLimited == true -> "Mejor tarifa observada hasta ahora"
                current && alert.flexibleDateSearch -> "Mejor tarifa observada en el rango"
                current -> "Tarifa más baja observada"
                else -> "Último resultado"
            }
            val offers = result?.verifiedOffersJson
                ?.let { runCatching { Json.decodeFromString<List<VerifiedRoundTripOffer>>(it) }.getOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: result?.selectedOfferJson?.let { raw -> runCatching { listOf(Json.decodeFromString<VerifiedRoundTripOffer>(raw)) }.getOrNull() }.orEmpty()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${formatAlertDates(alert)} · ${alertPassengerSummary(alert)} · Económica", color = Color.White.copy(alpha = 0.84f), fontWeight = FontWeight.Medium)
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    Column {
                        SketchSectionHeader(detailHeader, AppIcon.Flight)
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (alert.returnDate.isBlank()) "Total solo ida" else "Total de ida y vuelta", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                        if (result?.lastVerifiedTotalCop != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(formatCop(result.lastVerifiedTotalCop), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Surface(color = if (current) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        if (current) AppIcon(AppIcon.Verified, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                        Text(
                                            if (current && result?.explorationLimited == true) "Verificado · parcial" else if (current) "Verificado" else "Dato anterior",
                                            color = if (current) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                            }
                            Text(if (alert.returnDate.isBlank()) "COP · solo ida" else "COP · ida y vuelta", color = MaterialTheme.colorScheme.secondary)
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
                            Text(if (alert.returnDate.isBlank()) "La app mostrará aquí el precio observado para el vuelo de ida." else "La app mostrará aquí un total solo cuando pueda verificar la ida y el regreso asociados.", color = MaterialTheme.colorScheme.secondary)
                        }
                        HorizontalDivider()
                        if (offers.isNotEmpty()) {
                            val best = offers.minByOrNull { it.totalCop }!!
                            offerDatesSummary(best)?.let { Text("Fechas de esta tarifa: $it", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
                            FlightLegSummary("Ida", best.outboundAirline, best.outbound)
                            HorizontalDivider()
                            if (best.returning.isNotEmpty()) FlightLegSummary("Regreso", best.returnAirline, best.returning)
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
                                Text(if (alert.flexibleDateSearch) "Exploración del rango" else "Exploración parcial", fontWeight = FontWeight.SemiBold, color = FlightAppColors.tealDark)
                                Text(
                                    when {
                                        alert.flexibleDateSearch && result.datePairsChecked < result.datePairCount && java.lang.Long.bitCount(result.datePairIncompleteMask) > 0 -> "Se consultaron ${result.datePairsChecked} de ${result.datePairCount} combinaciones; además, quedaron opciones de vuelo sin verificar en ${java.lang.Long.bitCount(result.datePairIncompleteMask)} combinación(es). La tarifa es la mejor observada hasta ahora."
                                        alert.flexibleDateSearch && result.datePairsChecked < result.datePairCount -> "Combinaciones de fechas consultadas: ${result.datePairsChecked} de ${result.datePairCount}. La tarifa mostrada es la mejor observada hasta ahora; el ciclo seguirá en próximas revisiones."
                                        alert.flexibleDateSearch -> "Se consultaron todas las fechas, pero quedaron opciones de vuelo sin verificar en ${java.lang.Long.bitCount(result.datePairIncompleteMask)} combinación(es). El total es el mejor observado; el mínimo todavía podría cambiar."
                                        else -> "No se comprobaron todas las opciones de vuelo que devolvió la fuente; podría haber una tarifa menor."
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                result.lastError?.let { error ->
                                    Text("Incidencia en la última revisión: ${readableSearchError(error)} ($error).", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
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
                            SketchSectionHeader(if (alert.returnDate.isBlank()) "Otras tarifas de ida" else "Tarifas de ida y vuelta", AppIcon.Flight)
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            offers.sortedBy { it.totalCop }.drop(1).forEachIndexed { index, offer ->
                                if (index > 0) HorizontalDivider()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    AirlineIdentity(
                                        verifiedOfferAirlines(offer),
                                        Modifier.widthIn(max = 148.dp),
                                        compact = true,
                                        wrapText = true,
                                    )
                                    offerDepartureTimes(offer)?.let { times ->
                                        Text(
                                            times,
                                            color = MaterialTheme.colorScheme.secondary,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.semantics {
                                                contentDescription = "Horas de salida: $times"
                                            },
                                        )
                                    }
                                    Text(
                                        formatCop(offer.totalCop),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            Text(if (alert.returnDate.isBlank()) "Precios de ida observados" else "Totales de ida y vuelta", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
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
                ReviewFlightButton(
                    modifier = Modifier.fillMaxWidth().primaryButtonTarget(),
                    onClick = { viewModel.requestReview(alert.id) },
                    isRunning = state.runningAlertId == alert.id,
                    idleLabel = "Revisar ahora",
                    canStart = state.runningAlertId == null,
                )
                Text(if (alert.returnDate.isBlank()) "Consume 1 consulta de vuelos." else "Consume hasta 9 consultas de vuelos.", modifier = Modifier.align(Alignment.CenterHorizontally), color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatAlertDates(outbound: String, returning: String): String {
    val start = LocalDate.parse(outbound)
    if (returning.isBlank()) return "Solo ida · ${DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO")).format(start)}"
    val end = LocalDate.parse(returning)
    val locale = Locale.forLanguageTag("es-CO")
    return if (start.month == end.month && start.year == end.year) {
        "${start.dayOfMonth}–${DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(end)}"
    } else {
        "${DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(start)}–${DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(end)}"
    }
}

private fun formatAlertDates(alert: FlightAlertEntity, compact: Boolean = false): String {
    if (!alert.flexibleDateSearch) return formatAlertDates(alert.outboundDate, alert.returnDate)
    val outboundEnd = alert.outboundDateEnd.ifBlank { alert.outboundDate }
    val windowFormatter: (String, String) -> String = if (compact) ::compactDateWindow else ::shortDateWindow
    val outbound = windowFormatter(alert.outboundDate, outboundEnd)
    if (alert.returnDate.isBlank()) return "Solo ida · Ida $outbound"
    val returnEnd = alert.returnDateEnd.ifBlank { alert.returnDate }
    return "Ida $outbound · Regreso ${windowFormatter(alert.returnDate, returnEnd)}"
}

private fun compactDateWindow(startValue: String, endValue: String): String {
    val start = LocalDate.parse(startValue)
    val end = LocalDate.parse(endValue)
    val month = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es-CO"))
    return when {
        start == end -> "${start.dayOfMonth} ${month.format(start)}"
        start.month == end.month && start.year == end.year -> "${start.dayOfMonth}–${end.dayOfMonth} ${month.format(end)}"
        else -> "${DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO")).format(start)}–${DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO")).format(end)}"
    }
}

private fun shortDateWindow(startValue: String, endValue: String): String {
    val start = LocalDate.parse(startValue)
    val end = LocalDate.parse(endValue)
    val monthPattern = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es-CO"))
    val yearPattern = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))
    if (start == end) return yearPattern.format(start)
    return if (start.month == end.month && start.year == end.year) {
        "${start.dayOfMonth}–${end.dayOfMonth} ${monthPattern.format(end)} ${end.year}"
    } else "${yearPattern.format(start)}–${yearPattern.format(end)}"
}

private fun offerDepartureDate(value: String?): String? = value?.take(10)?.let { raw ->
    runCatching { DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CO")).format(LocalDate.parse(raw)) }.getOrNull()
}

private fun alertPassengerSummary(alert: FlightAlertEntity): String {
    val minors = runCatching { Json.decodeFromString<List<MinorPassenger>>(alert.minorPassengersJson) }.getOrDefault(emptyList())
    return PassengerSelection.summary(alert.adults18Plus, minors)
}

private fun formatShortLocal(epochMillis: Long): String = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("es-CO"))
    .withZone(COLOMBIA_ZONE).format(Instant.ofEpochMilli(epochMillis))

@Composable
private fun FlightLegSummary(label: String, airline: String, segments: List<com.flightnotifications.app.domain.model.AlertFlightSegment>) {
    val stops = (segments.size - 1).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        AirlineIdentity(airline, Modifier.fillMaxWidth(), compact = true)
        departureTimesSummary(segments)?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(if (stops <= 0) "Directo" else "$stops escala(s)", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
    }
}

private fun departureTimesSummary(segments: List<com.flightnotifications.app.domain.model.AlertFlightSegment>): String? {
    val departures = segments.mapNotNull { segment ->
        FlightDepartureTime.format(segment.departureLocal)?.let { time ->
            if (segments.size == 1) "Salida $time" else "${segment.fromIata} $time"
        }
    }
    return departures.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun verifiedOfferAirlines(offer: VerifiedRoundTripOffer): String = when {
    offer.returning.isEmpty() -> offer.outboundAirline
    offer.outboundAirline.equals(offer.returnAirline, ignoreCase = true) -> offer.outboundAirline
    else -> "${offer.outboundAirline} / ${offer.returnAirline}"
}

private fun offerDepartureTimes(offer: VerifiedRoundTripOffer): String? {
    fun firstDeparture(segments: List<com.flightnotifications.app.domain.model.AlertFlightSegment>): String? =
        segments.firstOrNull()?.departureLocal?.let(FlightDepartureTime::format)

    val outbound = firstDeparture(offer.outbound)
    val returning = firstDeparture(offer.returning)
    return when {
        outbound != null && returning != null -> "$outbound / $returning"
        outbound != null -> outbound
        returning != null -> returning
        else -> null
    }
}

private fun offerDatesSummary(offer: VerifiedRoundTripOffer): String? {
    fun date(segments: List<com.flightnotifications.app.domain.model.AlertFlightSegment>): String? = segments.firstOrNull()?.departureLocal
        ?.take(10)?.let { runCatching { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO")).format(LocalDate.parse(it)) }.getOrNull() }
    val outbound = date(offer.outbound) ?: return null
    val returning = date(offer.returning)
    return if (returning == null) "ida $outbound" else "ida $outbound · regreso $returning"
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
    airportCatalog: AirportCatalog,
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
                if (alert.returnDate.isNotBlank()) AirportRouteLine("Regreso", airportFullName(alert.destinationIata, airportCatalog))
                Text(formatAlertDates(alert), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = alert.enabled && !expired, onCheckedChange = onToggle, enabled = !isRunning && !expired, modifier = Modifier.testTag("toggle-alert-${alert.id}").sizeIn(minWidth = 48.dp, minHeight = 48.dp))
        }
        Text("${alertPassengerSummary(alert)} · Máximo ${formatCop(alert.maxTotalCop)} · cada ${alert.frequencyHours} h · ${if (alert.directOnly) "directos" else "escalas permitidas"} · ${when { expired -> "vencida"; alert.enabled -> "activa"; else -> "pausada" }}")
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
        val stateDescription = when {
            currentVerification && result.explorationLimited -> "Tarifa verificada; búsqueda parcial"
            currentVerification -> "Tarifa verificada"
            result.lastVerifiedTotalCop != null -> "Hay una tarifa anterior; no está confirmada con los criterios actuales"
            else -> result.status.replace('_', ' ')
        }
        Text("Estado: $stateDescription")
        result.accountAlias?.takeIf { it.isNotBlank() }?.let { Text("Cuenta SerpApi usada: $it.") }
        Text(
            "Salidas consideradas: ${result.candidatesConsidered} · respuestas completadas: ${result.candidatesCompleted} · fallidas: ${result.candidateFailures} · GET de vuelos: ${result.flightGetsStarted}.",
        )
        result.lastError?.let { error ->
            Text("Incidencia de la última revisión: ${readableSearchError(error)} ($error).")
        }
        result.networkReceivedAt?.let { Text("Respuesta recibida: ${formatLocal(it)}") }
        if (alert.flexibleDateSearch) {
            val pairCount = result.datePairCount.coerceAtLeast(1)
            val checked = result.datePairsChecked.coerceIn(0, pairCount)
            Text("Combinaciones de fechas consultadas: $checked de $pairCount.")
        }
        Text(
            if (result.explorationLimited) {
                val incompletePairs = java.lang.Long.bitCount(result.datePairIncompleteMask)
                if (alert.flexibleDateSearch && result.datePairsChecked < result.datePairCount && incompletePairs > 0) {
                    "Mejor tarifa observada hasta ahora. Faltan fechas por consultar y quedaron opciones de vuelo sin verificar en $incompletePairs combinación(es)."
                } else if (alert.flexibleDateSearch && result.datePairsChecked < result.datePairCount) {
                    "Mejor tarifa observada hasta ahora; faltan combinaciones de fechas por consultar."
                } else if (alert.flexibleDateSearch && incompletePairs > 0) {
                    "Se consultaron todas las fechas, pero quedaron opciones de vuelo sin verificar en $incompletePairs combinación(es). Se revisan hasta ${result.candidateLimit} salidas por consulta; el total mostrado es la mejor tarifa observada, no un mínimo confirmado."
                } else {
                    "Se revisaron ${result.candidatesConsidered} opciones de salida; la fuente no permitió verificar todas."
                }
            } else {
                "Se revisaron ${result.candidatesConsidered} opciones de salida; la fuente no informó más candidatas."
            },
        )
        if (result.lastVerifiedTotalCop != null) {
            val totalLabel = when {
                !currentVerification -> "Último total verificado; no confirmado con los criterios actuales"
                alert.flexibleDateSearch && result.explorationLimited -> "Mejor total verificado hasta ahora"
                alert.flexibleDateSearch -> "Mejor total verificado en el rango"
                else -> "Total verificado en esta consulta"
            }
            Text("$totalLabel: ${formatCop(result.lastVerifiedTotalCop)}")
            if (!currentVerification) {
                Text("Criterios del último total verificado: ${result.verifiedCriteria?.let(::formatCriteriaKey) ?: "no disponibles"} · ${result.lastVerifiedAt?.let(::formatLocal) ?: "fecha no disponible"}.")
            }
            if (verifiedOffers.isNotEmpty()) {
                val offersLabel = when {
                    !currentVerification -> "Opciones de la última consulta verificada"
                    alert.flexibleDateSearch -> "Opciones verificadas acumuladas en el rango"
                    else -> "Opciones verificadas en esta consulta"
                }
                Text("$offersLabel (${verifiedOffers.size})", fontWeight = FontWeight.Medium)
                verifiedOffers.forEachIndexed { index, offer ->
                    OfferSummary(offer, if (currentVerification) "Opción ${index + 1}" else "Dato previo · opción ${index + 1}")
                }
            }
        }
        if (airlines.isNotEmpty()) {
            val verifiedCarriers = if (currentVerification) {
                verifiedOffers.flatMap { offer -> listOfNotNull(offer.outboundAirline.takeIf(String::isNotBlank), offer.returnAirline.takeIf(String::isNotBlank)) }
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
                                        if (result.explorationLimited) append(" La búsqueda fue parcial y puede haber fechas o opciones de vuelo sin verificar.")
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
        offerDatesSummary(offer)?.let { Text("Fechas: $it", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Ida · ${offer.outbound.firstOrNull()?.fromIata ?: "?"} → ${offer.outbound.lastOrNull()?.toIata ?: "?"}", modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            AirlineIdentity(offer.outboundAirline, Modifier.weight(1f), compact = true)
        }
        Text(
            listOfNotNull(if (outStops == 0) "Directo" else "$outStops escala(s)", departureTimesSummary(offer.outbound)).joinToString(" · "),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall,
        )
        if (offer.returning.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Regreso · ${offer.returning.firstOrNull()?.fromIata ?: "?"} → ${offer.returning.lastOrNull()?.toIata ?: "?"}", modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                AirlineIdentity(offer.returnAirline, Modifier.weight(1f), compact = true)
            }
            Text(
                listOfNotNull(if (backStops == 0) "Directo" else "$backStops escala(s)", departureTimesSummary(offer.returning)).joinToString(" · "),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("${if (offer.returning.isEmpty()) "Precio solo ida" else "Total de ida y vuelta"} observado: ${formatCop(offer.totalCop)}. Verifica condiciones y disponibilidad antes de comprar.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TripTypeSelector(roundTrip: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "Solo ida", true to "Ida y vuelta").forEach { (isRoundTrip, label) ->
            val selected = roundTrip == isRoundTrip
            Surface(
                modifier = Modifier.weight(1f)
                    .selectable(selected = selected, onClick = { onSelect(isRoundTrip) }, role = Role.RadioButton)
                    .testTag(if (isRoundTrip) "trip-round-trip" else "trip-one-way"),
                shape = MaterialTheme.shapes.medium,
                color = if (selected) Color(0xFFFFA726) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) Color(0xFFEF6C00) else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        modifier = Modifier.size(44.dp),
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color(0xFFE65100),
                            unselectedColor = MaterialTheme.colorScheme.secondary,
                        ),
                    )
                    Text(
                        label,
                        color = Color(0xFF172A4A),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateSearchModeSelector(flexible: Boolean, onSelect: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Fechas del viaje", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !flexible,
                onClick = { onSelect(false) },
                label = { Text("Días específicos") },
                modifier = Modifier.weight(1f).testTag("date-mode-exact"),
            )
            FilterChip(
                selected = flexible,
                onClick = { onSelect(true) },
                label = { Text("Rango flexible") },
                modifier = Modifier.weight(1f).testTag("date-mode-flexible"),
            )
        }
    }
}

@Composable
private fun PassengerSelectionEditor(
    adults18Plus: Int,
    minors: List<MinorPassenger>,
    onAdultsChange: (Int) -> Unit,
    onMinorsChange: (List<MinorPassenger>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var showAgeDialog by remember { mutableStateOf(false) }
    var ageText by remember(showAgeDialog) { mutableStateOf("") }
    var infantSeating by remember(showAgeDialog) { mutableStateOf<String?>(null) }
    val enteredAge = ageText.toIntOrNull()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pasajeros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(color = FlightAppColors.paleBlue, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 2.dp, end = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Adultos · 18 años o más", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text("$adults18Plus ${if (adults18Plus == 1) "adulto" else "adultos"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { if (adults18Plus > 1) onAdultsChange(adults18Plus - 1) }, enabled = adults18Plus > 1, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("adult-minus")) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text(adults18Plus.toString(), modifier = Modifier.widthIn(min = 24.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { if (adults18Plus + minors.size < PassengerSelection.MAX_PASSENGERS) onAdultsChange(adults18Plus + 1) }, enabled = adults18Plus + minors.size < PassengerSelection.MAX_PASSENGERS, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("adult-plus")) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }
        }
        minors.forEachIndexed { index, passenger ->
            val passengerLabel = when (passenger.age) {
                in 0..1 -> "Bebé de ${passenger.age} ${if (passenger.age == 1) "año" else "años"} · ${if (passenger.infantSeating == "lap") "en brazos" else "en asiento"}"
                in 2..11 -> "Niño de ${passenger.age} años"
                else -> "Adolescente de ${passenger.age} años"
            }
            Surface(color = FlightAppColors.paleBlue, shape = MaterialTheme.shapes.small) {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(passengerLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { onMinorsChange(minors.filterIndexed { itemIndex, _ -> itemIndex != index }) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Quitar", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        Button(
            onClick = { showAgeDialog = true },
            enabled = adults18Plus + minors.size < PassengerSelection.MAX_PASSENGERS,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add-minor"),
            colors = ButtonDefaults.buttonColors(
                containerColor = FlightAppColors.teal,
                contentColor = Color.White,
            ),
        ) {
            Text("+ Agregar menor de 18 años", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
    if (showAgeDialog) {
        AlertDialog(
            onDismissRequest = { showAgeDialog = false },
            title = { Text("Edad del menor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it.filter(Char::isDigit).take(2); infantSeating = null },
                        label = { Text("Edad en años (0–17)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        modifier = Modifier.fillMaxWidth().testTag("minor-age"),
                    )
                    if (enteredAge != null && enteredAge in 0..1) {
                        Text("Para menores de 2 años, selecciona cómo viajarán:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = infantSeating == "lap", onClick = { infantSeating = "lap" }, label = { Text("En brazos") }, modifier = Modifier.testTag("infant-lap"))
                            FilterChip(selected = infantSeating == "seat", onClick = { infantSeating = "seat" }, label = { Text("En asiento") }, modifier = Modifier.testTag("infant-seat"))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = enteredAge != null && enteredAge in 0..17 && (enteredAge > 1 || infantSeating != null),
                    onClick = {
                        val age = enteredAge
                        if (age != null && age in 0..17 && adults18Plus + minors.size < PassengerSelection.MAX_PASSENGERS) {
                            onMinorsChange(minors + MinorPassenger(age, infantSeating.takeIf { age <= 1 }))
                            showAgeDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm-minor-age"),
                ) { Text("Agregar") }
            },
            dismissButton = { TextButton(onClick = { showAgeDialog = false }) { Text("Cancelar") } },
        )
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

private fun readableSearchError(code: String): String = when (code) {
    "SERVICE_ERROR" -> "falló el servicio de búsqueda"
    "SERVICE_UNAVAILABLE" -> "el servicio de búsqueda no está disponible"
    "RATE_LIMITED" -> "se alcanzó un límite temporal"
    "NETWORK" -> "hubo un problema de conexión"
    else -> code.replace('_', ' ').lowercase(Locale.ROOT)
}

private fun isCurrentVerification(alert: FlightAlertEntity, result: com.flightnotifications.app.data.local.FlightAlertResultEntity): Boolean =
    result.status == "VERIFICADO" && result.verifiedCriteria != null &&
        result.verifiedCriteria == result.attemptCriteria && result.verifiedCriteria == criteriaKey(alert) &&
        result.attemptMaximumCop == alert.maxTotalCop

private fun criteriaKey(alert: FlightAlertEntity): String =
    if (!alert.flexibleDateSearch) {
        "${alert.originIata}-${alert.destinationIata}|${alert.outboundDate}|${alert.returnDate.ifBlank { "one-way" }}|direct=${alert.directOnly}|adults=${alert.adults18Plus}|minors=${alert.minorPassengersJson}"
    } else {
        val outboundEnd = alert.outboundDateEnd.ifBlank { alert.outboundDate }
        val returnEnd = alert.returnDateEnd.ifBlank { alert.returnDate }
        "${alert.originIata}-${alert.destinationIata}|${alert.outboundDate}..$outboundEnd|${if (alert.returnDate.isBlank()) "one-way" else "${alert.returnDate}..$returnEnd"}|direct=${alert.directOnly}|adults=${alert.adults18Plus}|minors=${alert.minorPassengersJson}|flexible=true"
    }

private fun formatCriteriaKey(value: String): String {
    val parts = value.split('|')
    val route = parts.getOrNull(0)?.replace('-', '→') ?: return "no disponibles"
    val outbound = parts.getOrNull(1) ?: return value
    val returning = parts.getOrNull(2) ?: return value
    val direct = parts.getOrNull(3)?.substringAfter('=')?.toBooleanStrictOrNull()
    val adults = parts.getOrNull(4)?.substringAfter('=')?.toIntOrNull() ?: 1
    val minors = parts.getOrNull(5)?.substringAfter('=')?.let { raw -> runCatching { Json.decodeFromString<List<MinorPassenger>>(raw) }.getOrDefault(emptyList()) }.orEmpty()
    val flexible = parts.getOrNull(6) == "flexible=true"
    val trip = when {
        returning == "one-way" -> "solo ida"
        flexible -> "regreso en rango $returning"
        else -> "regreso $returning"
    }
    val outboundLabel = if (flexible) "rango de ida $outbound" else "ida $outbound"
    return "$route · $outboundLabel · $trip · ${PassengerSelection.summary(adults, minors)} · ${if (direct == true) "directos" else if (direct == false) "escalas permitidas" else "filtros no disponibles"}"
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
