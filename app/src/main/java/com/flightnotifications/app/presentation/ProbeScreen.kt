package com.flightnotifications.app.presentation

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flightnotifications.app.domain.model.AccountSnapshot
import com.flightnotifications.app.domain.model.FlightOption
import com.flightnotifications.app.domain.model.FlightDepartureTime
import com.flightnotifications.app.domain.model.ProbeOutcome
import com.flightnotifications.app.domain.model.SearchCriteria
import com.flightnotifications.app.domain.model.VerifiedRoundTripOffer
import com.flightnotifications.app.domain.model.MinorPassenger
import com.flightnotifications.app.domain.model.PassengerSelection
import com.flightnotifications.app.domain.validation.AirportCatalog
import com.flightnotifications.app.ui.theme.FlightAppColors
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private enum class AppPage { ALERTS, ACTIVITY, SETTINGS, DIAGNOSTIC, EDITOR, DETAIL }

private val AppPage.isPrimary: Boolean
    get() = this == AppPage.ALERTS || this == AppPage.ACTIVITY || this == AppPage.SETTINGS

@Composable
fun ProbeScreen(viewModel: ProbeViewModel, alertsViewModel: FlightAlertsViewModel? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(AppPage.ALERTS) }
    var returnPage by remember { mutableStateOf(AppPage.ALERTS) }
    var editorAlertId by remember { mutableStateOf<String?>(null) }
    var selectedAlertId by remember { mutableStateOf<String?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(alertsViewModel?.notificationsAllowed() == true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, alertsViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = alertsViewModel?.notificationsAllowed() == true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val alertsState = if (alertsViewModel == null) {
        FlightAlertsUiState()
    } else {
        val value by alertsViewModel.state.collectAsStateWithLifecycle()
        value
    }

    SideEffect {
        activity?.window?.let { window ->
            window.statusBarColor = FlightAppColors.flightBlue.toArgb()
            window.navigationBarColor = (if (page.isPrimary) Color.White else FlightAppColors.flightBlueDark).toArgb()
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
            window.decorView.windowInsetsController?.setSystemBarsAppearance(
                if (page.isPrimary) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        }
    }

    fun openSubpage(next: AppPage) {
        returnPage = page.takeIf { it.isPrimary } ?: returnPage
        page = next
    }

    fun goBack() {
        page = returnPage.takeIf { it.isPrimary } ?: AppPage.ALERTS
    }

    BackHandler(enabled = !page.isPrimary) { goBack() }

    Scaffold(
        modifier = Modifier.background(Brush.verticalGradient(listOf(FlightAppColors.flightBlue, FlightAppColors.flightBlueDark))),
        containerColor = Color.Transparent,
        bottomBar = {
            if (page.isPrimary) {
                FlightBottomNavigation(
                    selected = when (page) {
                        AppPage.ACTIVITY -> MainTab.ACTIVITY
                        AppPage.SETTINGS -> MainTab.SETTINGS
                        else -> MainTab.ALERTS
                    },
                    onSelect = { tab ->
                        page = when (tab) {
                            MainTab.ALERTS -> AppPage.ALERTS
                            MainTab.ACTIVITY -> AppPage.ACTIVITY
                            MainTab.SETTINGS -> AppPage.SETTINGS
                        }
                    },
                )
            }
        },
    ) { insets ->
        when (page) {
            AppPage.ALERTS -> Column(
                modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (alertsViewModel != null) {
                    FlightAlertsSection(
                        viewModel = alertsViewModel,
                        onCreateAlert = { editorAlertId = null; openSubpage(AppPage.EDITOR) },
                        onEditAlert = { id -> editorAlertId = id; openSubpage(AppPage.EDITOR) },
                        onOpenDetail = { id -> selectedAlertId = id; openSubpage(AppPage.DETAIL) },
                    )
                } else EmptyAppCard("No se pudo cargar el administrador de alertas.")
            }
            AppPage.ACTIVITY -> Column(
                modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActivityScreen(
                    state = alertsState,
                    airportCatalog = alertsViewModel?.airportCatalog,
                    onClear = { alertsViewModel?.requestClearNotifications() },
                    onOpenAlert = { id -> selectedAlertId = id; openSubpage(AppPage.DETAIL) },
                )
            }
            AppPage.SETTINGS -> Column(
                modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsScreen(
                    state = state,
                    credentialBusy = state.isRunningProbe || state.isCheckingAccount || state.isDeletingCredential,
                    alertsViewModel = alertsViewModel,
                    notificationsAllowed = notificationsAllowed,
                    onSaveCredential = viewModel::saveCredential,
                    onDeleteCredential = { pendingDelete = it },
                    onCheckAccount = viewModel::checkAccount,
                    onReduceBudget = viewModel::reduceLocalBudgetLimit,
                    onCopyLastSearchLog = {
                        val copied = runCatching {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                ?: error("Portapapeles no disponible")
                            clipboard.setPrimaryClip(ClipData.newPlainText("Log de la última búsqueda", state.flightDiagnosticLog))
                        }
                        Toast.makeText(
                            context,
                            if (copied.isSuccess) "Log de la última búsqueda copiado" else "No se pudo copiar el log; puede ser demasiado grande",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    onOpenDiagnostic = { openSubpage(AppPage.DIAGNOSTIC) },
                    onOpenCatalog = { showCatalog = true },
                    onOpenNotificationSettings = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    },
                )
            }
            AppPage.DIAGNOSTIC -> DiagnosticScreen(
                state = state,
                modifier = Modifier.fillMaxSize().padding(insets),
                onBack = ::goBack,
                onCheckSchedule = alertsViewModel?.let { vm -> vm::checkScheduling } ?: {},
                schedulingStatus = alertsState.schedulingStatus,
                onOutboundDate = viewModel::setOutboundDate,
                onReturnDate = viewModel::setReturnDate,
                onDirectOnly = viewModel::setDirectOnly,
                onStartProbe = viewModel::requestFlightProbeConfirmation,
                onCancelProbe = viewModel::cancelFlightProbe,
                onClearAccountLog = viewModel::clearDiagnosticLog,
                onClearFlightLog = viewModel::clearFlightDiagnosticLog,
                onCopyAccountLog = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Registro de conexión SerpApi", state.accountDiagnosticLog))
                    Toast.makeText(context, "Registro copiado", Toast.LENGTH_SHORT).show()
                },
                onCopyFlightLog = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Registro de búsqueda de vuelos SerpApi", state.flightDiagnosticLog))
                    Toast.makeText(context, "Registro de vuelos copiado", Toast.LENGTH_SHORT).show()
                },
            )
            AppPage.EDITOR -> if (alertsViewModel != null) {
                FlightAlertEditorScreen(
                    viewModel = alertsViewModel,
                    alertId = editorAlertId,
                    maximumLocalGets = state.localBudgetLimit,
                    modifier = Modifier.fillMaxSize().padding(insets),
                    onBack = ::goBack,
                    onSaved = ::goBack,
                )
            } else EmptyAppCard("No se pudo cargar el editor de alertas.")
            AppPage.DETAIL -> if (alertsViewModel != null) {
                FlightAlertDetailScreen(
                    viewModel = alertsViewModel,
                    alertId = selectedAlertId,
                    modifier = Modifier.fillMaxSize().padding(insets),
                    onBack = ::goBack,
                    onEdit = { id -> editorAlertId = id; returnPage = AppPage.DETAIL; page = AppPage.EDITOR },
                    onShowCatalog = { showCatalog = true },
                )
            } else EmptyAppCard("No se pudo cargar el detalle de la alerta.")
        }
    }

    if (state.showFlightConfirmation) {
        val criteria = SearchCriteria(state.outboundDate, state.returnDate, state.directOnly)
        AlertDialog(
            onDismissRequest = viewModel::dismissFlightProbeConfirmation,
            title = { Text("Autorizar prueba de vuelos") },
            text = {
                Text(
                    "${criteria.origin} → ${criteria.destination} · ${criteria.outboundDate}\n" +
                        "${criteria.destination} → ${criteria.origin} · ${criteria.returnDate}\n" +
                        "${if (criteria.directOnly) "Solo vuelos directos" else "Se permiten escalas"}\n\n" +
                        "Al continuar se comprobará primero la cuenta y, si hay cuota suficiente, se iniciarán hasta 2 GET de vuelos. " +
                        "Cancelar aquí inicia 0 GET de vuelos. La llamada gratuita de Account API se hace antes de la tanda."
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmFlightProbe, modifier = Modifier.secondaryButtonTarget()) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissFlightProbeConfirmation, modifier = Modifier.secondaryButtonTarget()) { Text("Cancelar") } },
        )
    }

    pendingDelete?.let { credentialId ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar API key") },
            text = { Text("Se quitará esta cuenta de la lista local. Las demás API keys seguirán disponibles para las búsquedas.") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; viewModel.deleteCredential(credentialId) }, modifier = Modifier.secondaryButtonTarget()) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }, modifier = Modifier.secondaryButtonTarget()) { Text("Volver") } },
        )
    }

    if (alertsState.showClearNotificationsConfirmation && alertsViewModel != null) {
        AlertDialog(
            onDismissRequest = alertsViewModel::dismissClearNotifications,
            title = { Text("Limpiar avisos visibles") },
            text = { Text("Se vaciará la lista local y se cerrarán las notificaciones visibles. La próxima revisión puede volver a avisar la tarifa más baja.") },
            confirmButton = { TextButton(onClick = alertsViewModel::confirmClearNotifications, modifier = Modifier.secondaryButtonTarget()) { Text("Limpiar") } },
            dismissButton = { TextButton(onClick = alertsViewModel::dismissClearNotifications, modifier = Modifier.secondaryButtonTarget()) { Text("Cancelar") } },
        )
    }

    if (showCatalog && alertsViewModel != null) {
        AlertDialog(
            onDismissRequest = { showCatalog = false },
            title = { Text("Aerolíneas nacionales · ${alertsViewModel.airlineCatalog.reviewedAt}") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    alertsViewModel.airlineCatalog.airlines.forEach { AirlineIdentity("${it.name} (${it.code})", Modifier.fillMaxWidth(), compact = true) }
                    Text(alertsViewModel.airlineCatalog.source, style = MaterialTheme.typography.bodySmall)
                    Text("Catálogo informativo, no exhaustivo. No filtra la búsqueda ni garantiza tarifas o cobertura.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showCatalog = false }, modifier = Modifier.secondaryButtonTarget()) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun ActivityScreen(
    state: FlightAlertsUiState,
    airportCatalog: AirportCatalog?,
    onClear: () -> Unit,
    onOpenAlert: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Actividad", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Surface(
                modifier = Modifier.clickable(enabled = state.notifications.isNotEmpty(), onClick = onClear).secondaryButtonTarget(),
                color = Color.White.copy(alpha = 0.14f),
                shape = CircleShape,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppIcon(AppIcon.Delete, null, tint = Color.White)
                    Text("Limpiar", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (state.notifications.isEmpty()) {
            EmptyAppCard("Todavía no hay avisos. Cuando una revisión encuentre una tarifa que cumpla tu máximo, aparecerá aquí.")
        } else {
            val zone = ZoneId.of("America/Bogota")
            val grouped = state.notifications
                .sortedByDescending { it.sentAt }
                .groupBy { Instant.ofEpochMilli(it.sentAt).atZone(zone).toLocalDate() }
            val today = LocalDate.now(zone)
            grouped.forEach { (day, notices) ->
                Text(activityDayLabel(day, today), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                notices.forEach { notice ->
                    val alertItem = state.alerts.firstOrNull { it.alert.id == notice.alertId }
                    val alert = alertItem?.alert
                    val matchingOffer = remember(notice.itineraryIdentity, alertItem?.result?.selectedOfferJson, alertItem?.result?.verifiedOffersJson) {
                        val result = alertItem?.result
                        val verifiedOffers = result?.verifiedOffersJson
                            ?.let { runCatching { Json.decodeFromString<List<VerifiedRoundTripOffer>>(it) }.getOrNull() }
                            .orEmpty()
                        (verifiedOffers + listOfNotNull(
                            result?.selectedOfferJson?.let { raw -> runCatching { Json.decodeFromString<VerifiedRoundTripOffer>(raw) }.getOrNull() },
                        )).firstOrNull { it.itineraryIdentity == notice.itineraryIdentity }
                    }
                    val origin = alert?.originIata?.let { airportCatalog?.find(it) }
                    val destination = alert?.destinationIata?.let { airportCatalog?.find(it) }
                    ActivityNoticeCard(
                        route = notice.routeSummary,
                        originCity = origin?.city,
                        originAirport = origin?.let(::airportShortName),
                        destinationCity = destination?.city,
                        destinationAirport = destination?.let(::airportShortName),
                        dates = alert?.let {
                            val minors = runCatching { Json.decodeFromString<List<MinorPassenger>>(it.minorPassengersJson) }.getOrDefault(emptyList())
                            val selectedDates = if (it.flexibleDateSearch) matchingOffer?.let(::formatOfferTripDates)
                                ?: formatTripDates(it.outboundDate, it.returnDate)
                            else formatTripDates(it.outboundDate, it.returnDate)
                            "$selectedDates · ${PassengerSelection.summary(it.adults18Plus, minors)}"
                        } ?: "",
                        price = notice.priceCop,
                        carriers = listOf(notice.outboundCarrier, notice.returnCarrier).filter(String::isNotBlank).joinToString(" / "),
                        outboundDepartureLocal = notice.outboundDepartureLocal ?: matchingOffer?.outbound?.firstOrNull()?.departureLocal,
                        returnDepartureLocal = notice.returnDepartureLocal ?: matchingOffer?.returning?.firstOrNull()?.departureLocal,
                        sentAt = notice.sentAt,
                        onClick = { onOpenAlert(notice.alertId) },
                    )
                }
            }
            Text("Hasta 100 avisos locales. Limpiar cierra también las notificaciones visibles del sistema.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f))
        }
    }
}

@Composable
private fun ActivityNoticeCard(
    route: String,
    originCity: String?,
    originAirport: String?,
    destinationCity: String?,
    destinationAirport: String?,
    dates: String,
    price: Long,
    carriers: String,
    outboundDepartureLocal: String?,
    returnDepartureLocal: String?,
    sentAt: Long,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppIcon(AppIcon.Verified, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text("Verificada", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Text(
                if (originCity != null && destinationCity != null) "$originCity   ↔   $destinationCity" else route,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (originAirport != null || destinationAirport != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(originAirport.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(destinationAirport.orEmpty(), Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Text(dates, color = MaterialTheme.colorScheme.secondary)
            ActivityDepartureTimes(outboundDepartureLocal, returnDepartureLocal)
            Text(formatCop(price), style = MaterialTheme.typography.headlineMedium, color = FlightAppColors.tealDark, fontWeight = FontWeight.Bold)
            Text("COP · ida y vuelta", color = MaterialTheme.colorScheme.secondary)
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AirlineIdentity(carriers, Modifier.weight(1f), compact = true)
                Text(formatShortTime(sentAt), color = MaterialTheme.colorScheme.secondary)
                AppIcon(AppIcon.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun ActivityDepartureTimes(outboundDepartureLocal: String?, returnDepartureLocal: String?) {
    val outbound = FlightDepartureTime.format(outboundDepartureLocal)
    val returning = FlightDepartureTime.format(returnDepartureLocal)
    if (outbound == null && returning == null) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val departures = listOfNotNull(outbound?.let { "Ida" to it }, returning?.let { "Regreso" to it })
        departures.forEach { (label, time) ->
            val tint = if (label == "Ida") Color(0xFF1767C9) else Color(0xFF6D42C6)
            val background = if (label == "Ida") FlightAppColors.paleBlue else FlightAppColors.paleLavender
            Surface(Modifier.weight(1f), color = background, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Salida $label", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text(time, color = tint, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: ProbeUiState,
    credentialBusy: Boolean,
    alertsViewModel: FlightAlertsViewModel?,
    notificationsAllowed: Boolean,
    onSaveCredential: (String) -> Unit,
    onDeleteCredential: (String) -> Unit,
    onCheckAccount: () -> Unit,
    onReduceBudget: (Int) -> Unit,
    onCopyLastSearchLog: () -> Unit,
    onOpenDiagnostic: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0),
        ).versionName.orEmpty()
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(72.dp)) {
            FlightSkyDecoration(Modifier.fillMaxSize())
            Text("Ajustes", modifier = Modifier.align(Alignment.CenterStart), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
        CredentialCard(
            configured = state.credentialConfigured,
            accounts = state.credentialAccounts,
            busy = credentialBusy,
            saveCompleted = state.credentialSaveCompleted,
            saveError = state.credentialSaveError,
            accountMessage = state.accountMessage,
            accountActive = state.credentialAccounts.any { it.active },
            onSave = onSaveCredential,
            onDelete = onDeleteCredential,
            onCheckAccount = onCheckAccount,
        )
        LocalBudgetCard(
            maximum = state.localBudgetLimit,
            used = state.budget?.usedInWindow ?: 0,
            remaining = state.budget?.remainingInWindow ?: state.localBudgetLimit,
            onSave = onReduceBudget,
            officialAvailable = state.credentialAccounts.filter { it.active }.mapNotNull { it.searchesLeft }.sum().takeIf { state.credentialAccounts.isNotEmpty() },
            accountCheckedAt = state.account?.checkedAt?.toEpochMilli(),
        )
        Card(
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column {
                SketchSectionHeader("Herramientas y opciones", AppIcon.Settings)
                Column(Modifier.padding(horizontal = 14.dp)) {
                    SettingsRow(AppIcon.Notifications, "Notificaciones", if (notificationsAllowed) "Activadas en este dispositivo" else "Desactivadas en Android", onOpenNotificationSettings, success = notificationsAllowed)
                    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                    SettingsRow(AppIcon.Info, "Diagnóstico", "Programación, pruebas y registros", onOpenDiagnostic)
                    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                    SettingsRow(AppIcon.Flight, "Aerolíneas compatibles", "Catálogo informativo", onOpenCatalog)
                    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                    SettingsRow(
                        AppIcon.Copy,
                        "Copiar log de última búsqueda",
                        "Se reemplaza con cada búsqueda; claves y datos de cuenta se ocultan",
                        onCopyLastSearchLog,
                        enabled = state.flightDiagnosticLog.isNotBlank(),
                    )
                    state.flightDiagnosticStorageError?.let { error ->
                        Text(
                            "No se pudo guardar el log completo en el dispositivo ($error). La búsqueda siguió ejecutándose; copia el registro ahora para conservarlo.",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        Text("Alertas de vuelos · versión $appVersion", modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        state.errorMessage?.let { ErrorCard(it) }
        if (alertsViewModel == null) Text("No se pudo cargar el estado de alertas.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SettingsRow(
    icon: AppIcon,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    success: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(color = if (success) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(icon, null, modifier = Modifier.size(22.dp), tint = if (success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = if (success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
        }
        AppIcon(AppIcon.ChevronRight, null, tint = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun DiagnosticScreen(
    state: ProbeUiState,
    modifier: Modifier,
    onBack: () -> Unit,
    onCheckSchedule: () -> Unit,
    schedulingStatus: String,
    onOutboundDate: (LocalDate) -> Unit,
    onReturnDate: (LocalDate) -> Unit,
    onDirectOnly: (Boolean) -> Unit,
    onStartProbe: () -> Unit,
    onCancelProbe: () -> Unit,
    onClearAccountLog: () -> Unit,
    onClearFlightLog: () -> Unit,
    onCopyAccountLog: () -> Unit,
    onCopyFlightLog: () -> Unit,
) {
    var showFlightProbe by remember { mutableStateOf(false) }
    var showFlightLog by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BackIconButton(onBack)
            Column {
                Text("Diagnóstico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Estado del sistema y registros", color = Color.White.copy(alpha = 0.82f))
            }
        }
        Card(
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Programación automática", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val scheduled = schedulingStatus.contains("Enqueued", true) || schedulingStatus.contains("program", true)
                    Surface(color = if (scheduled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            AppIcon(if (scheduled) AppIcon.Verified else AppIcon.Clock, null, modifier = Modifier.size(16.dp), tint = if (scheduled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                            Text(if (scheduled) "Programada" else "Estado", color = if (scheduled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text(schedulingStatus, color = MaterialTheme.colorScheme.secondary)
                HorizontalDivider()
                Text("Android puede aplazar las revisiones por batería, Doze, conectividad o detención forzosa.", color = MaterialTheme.colorScheme.secondary)
                OutlinedButton(onClick = onCheckSchedule, modifier = Modifier.fillMaxWidth().primaryButtonTarget()) {
                    AppIcon(AppIcon.Calendar, null)
                    Text("Comprobar programación")
                }
                Text("No consume consultas de vuelos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { showFlightProbe = !showFlightProbe }) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Flight, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Prueba manual de vuelos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("CTG ↔ CLO · fechas y vuelos directos", color = MaterialTheme.colorScheme.secondary)
                    }
                    AppIcon(if (showFlightProbe) AppIcon.ExpandLess else AppIcon.ExpandMore, null, tint = MaterialTheme.colorScheme.secondary)
                }
                if (showFlightProbe) {
                    SearchCard(state, onOutboundDate, onReturnDate, onDirectOnly, onStartProbe, onCancelProbe)
                    state.errorMessage?.let { ErrorCard(it) }
                    state.outcome?.let { OutcomeCard(it) }
                }
            }
        }
        Text("Registro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showFlightLog,
                onClick = { showFlightLog = false },
                label = { Text("Conexión") },
                modifier = Modifier.chipTarget(),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
            )
            FilterChip(
                selected = showFlightLog,
                onClick = { showFlightLog = true },
                label = { Text("Vuelos") },
                modifier = Modifier.chipTarget(),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
            )
        }
        DiagnosticLogCard(
            title = if (showFlightLog) "Registro de búsqueda de vuelos" else "Registro de conexión",
            emptyMessage = if (showFlightLog) "Aún no hay eventos. Confirma una búsqueda para registrar el intento." else "Aún no hay eventos. Pulsa Probar conexión para registrar el intento.",
            logText = if (showFlightLog) state.flightDiagnosticLog.takeLast(MAX_FLIGHT_LOG_PREVIEW_CHARS) else state.accountDiagnosticLog,
            description = if (showFlightLog) {
                if (state.flightDiagnosticLog.length > MAX_FLIGHT_LOG_PREVIEW_CHARS) "Vista previa de las últimas líneas. Copiar incluye el log completo. Claves y datos de cuenta se ocultan." else "Incluye solicitudes, respuestas completas y validaciones de la última búsqueda. Claves y datos de cuenta se ocultan."
            } else "Registro local de comprobaciones de conexión. No incluye claves API.",
            onClear = if (showFlightLog) onClearFlightLog else onClearAccountLog,
            onCopy = if (showFlightLog) onCopyFlightLog else onCopyAccountLog,
        )
        Text("Los registros no contienen API keys, tokens ni cuerpos JSON. Las pruebas manuales de vuelos pueden consumir cuota.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f))
    }
}

@Composable
private fun EmptyAppCard(message: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(AppIcon.Info, null, tint = MaterialTheme.colorScheme.primary)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTripDates(outbound: String, returning: String): String {
    val format = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))
    val start = LocalDate.parse(outbound)
    if (returning.isBlank()) return "Solo ida · ${start.format(format)}"
    val end = LocalDate.parse(returning)
    return if (start.month == end.month && start.year == end.year) "${start.dayOfMonth}–${end.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO")))}"
    else "${start.format(format)}–${end.format(format)}"
}

private fun formatOfferTripDates(offer: VerifiedRoundTripOffer): String {
    val format = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))
    val outbound = offer.outbound.firstOrNull()?.departureLocal?.take(10)?.let { runCatching { LocalDate.parse(it).format(format) }.getOrNull() }
        ?: return "Fechas del vuelo no disponibles"
    val returning = offer.returning.firstOrNull()?.departureLocal?.take(10)?.let { runCatching { LocalDate.parse(it).format(format) }.getOrNull() }
    return if (returning == null) "Solo ida · $outbound" else "$outbound · regreso $returning"
}

private fun formatShortTime(epochMillis: Long): String = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.of("America/Bogota")).format(Instant.ofEpochMilli(epochMillis))

private fun activityDayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Hoy"
    today.minusDays(1) -> "Ayer"
    else -> DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("es-CO"))
        .format(day)
        .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-CO")) }
}

@Composable
private fun DiagnosticLogCard(
    title: String,
    emptyMessage: String,
    logText: String,
    description: String,
    onClear: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = logText.ifBlank { emptyMessage },
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 300.dp),
                label = { Text("Log de diagnóstico") },
                readOnly = true,
                minLines = 6,
                maxLines = 12,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopy, enabled = logText.isNotBlank(), modifier = Modifier.weight(1f).primaryButtonTarget()) {
                    AppIcon(AppIcon.Copy, null)
                    Text("Copiar")
                }
                OutlinedButton(onClick = onClear, enabled = logText.isNotBlank(), modifier = Modifier.weight(1f).primaryButtonTarget()) {
                    AppIcon(AppIcon.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Text("Limpiar")
                }
            }
        }
    }
}

private const val MAX_FLIGHT_LOG_PREVIEW_CHARS = 12_000

@Composable
private fun CredentialCard(
    configured: Boolean,
    accounts: List<CredentialAccountUi>,
    busy: Boolean,
    saveCompleted: Boolean,
    saveError: String?,
    accountMessage: String?,
    accountActive: Boolean,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCheckAccount: () -> Unit,
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var adding by remember(configured) { mutableStateOf(!configured) }
    LaunchedEffect(saveCompleted) {
        if (saveCompleted) {
            apiKeyInput = ""
            adding = false
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column {
            SketchSectionHeader("Cuenta y conexión", AppIcon.Shield)
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(
                    color = if (accountActive) Color(0xFFEAF7F1) else FlightAppColors.paleYellow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = if (accountActive) Color(0xFF17976F) else Color(0xFFE5A83E), shape = CircleShape, modifier = Modifier.size(34.dp)) {
                            Box(contentAlignment = Alignment.Center) { AppIcon(if (accountActive) AppIcon.Verified else AppIcon.Info, null, modifier = Modifier.size(20.dp), tint = Color.White) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (accountActive) "Cuentas disponibles" else if (configured) "Cuentas sin saldo o sin validar" else "Sin cuentas conectadas", color = if (accountActive) Color(0xFF157552) else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            Text(accountMessage ?: if (accountActive) "Las consultas usarán la primera cuenta con capacidad disponible." else "Añade una o más API keys de SerpApi.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(color = FlightAppColors.paleLavender, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) { AppIcon(AppIcon.Key, "Clave protegida", tint = Color(0xFF7C48BF)) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Cuentas de SerpApi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Cada API key pertenece a su propia cuenta", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (configured) OutlinedButton(
                        onClick = onCheckAccount,
                        enabled = !busy,
                        modifier = Modifier.secondaryButtonTarget(),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FlightAppColors.teal.copy(alpha = 0.6f)),
                    ) { Text("Actualizar", color = FlightAppColors.tealDark) }
                }
                if (accounts.isNotEmpty()) {
                    accounts.forEachIndexed { index, account ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (account.active) Color(0xFFF0F8F6) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(color = if (account.active) Color(0xFFD8F1E7) else FlightAppColors.paleLavender, shape = CircleShape, modifier = Modifier.size(34.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Text("${index + 1}", color = if (account.active) Color(0xFF157552) else MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(account.maskedKey, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(account.status, color = if (account.active) Color(0xFF157552) else MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { onDelete(account.id) }, enabled = !busy, modifier = Modifier.secondaryButtonTarget()) {
                                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                if (configured && !adding) {
                    OutlinedButton(
                        onClick = { adding = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().primaryButtonTarget(),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FlightAppColors.teal.copy(alpha = 0.55f)),
                    ) {
                        AppIcon(AppIcon.Add, null, tint = FlightAppColors.tealDark)
                        Text("Añadir API key", color = FlightAppColors.tealDark)
                    }
                }
                if (adding) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API key de otra cuenta") },
                        placeholder = { Text("Pega aquí la API key") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !busy,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { onSave(apiKeyInput) }, enabled = !busy && apiKeyInput.isNotBlank(), modifier = Modifier.primaryButtonTarget(), shape = CircleShape) {
                            Text(if (busy) "Validando…" else "Validar y añadir")
                        }
                        if (configured) TextButton(onClick = { adding = false; apiKeyInput = "" }, enabled = !busy, modifier = Modifier.secondaryButtonTarget()) { Text("Cancelar") }
                    }
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text("La clave se comprueba con el endpoint de cuenta y se guarda cifrada en este dispositivo. Las cuentas se prueban en el orden mostrado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun AccountCard(account: AccountSnapshot, message: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Estado de cuenta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message.orEmpty())
            Text("Saldo total informado: ${account.totalSearchesLeft?.toString() ?: "No disponible"}")
            Text("Saldo del plan: ${account.planSearchesLeft?.toString() ?: "No informado"}")
            Text("Uso mensual: ${account.thisMonthUsage?.toString() ?: "No informado"} / ${account.searchesPerMonth?.toString() ?: "No informado"}")
            Text("Límite horario: ${account.thisHourSearches ?: "?"} usados de ${account.hourlyLimit ?: "?"}; capacidad restante conservadora: ${account.conservativeSearchesLeft ?: "No verificable"}")
            Text("Renovación: ${account.planRenewalDate?.toString() ?: "No informada"}")
            Text("Comprobada: ${formatInstant(account.checkedAt)}")
        }
    }
}

@Composable
private fun SearchCard(
    state: ProbeUiState,
    onOutboundDate: (LocalDate) -> Unit,
    onReturnDate: (LocalDate) -> Unit,
    onDirectOnly: (Boolean) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val criteria = SearchCriteria(state.outboundDate, state.returnDate, state.directOnly)
    val dateError = criteria.validationError()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ensayo CTG ↔ CLO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DateButton("Ida", state.outboundDate, Modifier.weight(1f)) { current ->
                    openDatePicker(context, current, LocalDate.now(SearchCriteria.COLOMBIA_ZONE)) { selected ->
                        onOutboundDate(selected)
                        val returnStart = if (state.returnDate.isAfter(selected)) state.returnDate else selected.plusDays(1)
                        Handler(Looper.getMainLooper()).post {
                            openDatePicker(context, returnStart, selected.plusDays(1), onReturnDate)
                        }
                    }
                }
                DateButton("Regreso", state.returnDate, Modifier.weight(1f)) { current ->
                    openDatePicker(context, current, state.outboundDate.plusDays(1), onReturnDate)
                }
            }
            if (dateError != null) Text(dateError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Solo vuelos directos", fontWeight = FontWeight.Medium)
                    Text("Desactívalo para permitir escalas.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.directOnly, onCheckedChange = onDirectOnly, enabled = !state.isRunningProbe, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
            }
            HorizontalDivider()
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    AppIcon(AppIcon.Info, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("1 adulto · Económica · ida y vuelta", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                        Text("Se consultan todas las aerolíneas. No se envían filtros de precio.", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.budget?.let {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Presupuesto local de vuelos", style = MaterialTheme.typography.titleSmall)
                        Text("${it.usedInWindow} de ${it.maximumRequests} consultas usadas · ${it.remainingInWindow} disponibles")
                        Text("Ventana conservadora de 30 días; el teléfono apagado no adelanta el contador.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                if (!it.clockTrusted) {
                    Text(
                        "El reloj del dispositivo parece haber cambiado. Las consultas de vuelos permanecen bloqueadas hasta revisar y corregir la hora.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onStart,
                    enabled = state.credentialConfigured && !state.isRunningProbe && !state.isCheckingAccount && dateError == null,
                    modifier = Modifier.weight(1f).primaryButtonTarget(),
                ) {
                    AppIcon(AppIcon.Flight, null, tint = MaterialTheme.colorScheme.onPrimary)
                    Text("Buscar vuelos")
                }
                if (state.isRunningProbe) OutlinedButton(onClick = onCancel, modifier = Modifier.primaryButtonTarget()) { Text("Cancelar") }
            }
            if (state.isCheckingAccount || state.isRunningProbe) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    Text(if (state.isCheckingAccount) "Comprobando cuenta…" else "Consultando vuelos…")
                }
            }
        }
    }
}

@Composable
private fun LocalBudgetCard(
    maximum: Int,
    used: Int,
    remaining: Int,
    onSave: (Int) -> Unit,
    officialAvailable: Int?,
    accountCheckedAt: Long?,
) {
    var draft by remember(maximum) { mutableStateOf(maximum.toString()) }
    var showEditor by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column {
            SketchSectionHeader("Presupuesto de consultas", AppIcon.Calendar)
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(Modifier.weight(1f), color = FlightAppColors.paleBlue, shape = MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCEBF5))) {
                        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Saldo combinado · cuentas activas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            Text(officialAvailable?.toString() ?: "—", style = MaterialTheme.typography.headlineLarge, color = FlightAppColors.tealDark, fontWeight = FontWeight.Bold)
                            Text(accountCheckedAt?.let { "Consultado ${formatShortTime(it)}" } ?: "Saldo sin consultar", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Surface(Modifier.weight(1f), color = FlightAppColors.paleLavender, shape = MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4DCF4))) {
                        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Consultas disponibles en la app", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(remaining.toString(), style = MaterialTheme.typography.headlineLarge, color = Color(0xFF6042AD), fontWeight = FontWeight.Bold)
                                Text("de $maximum", modifier = Modifier.padding(bottom = 5.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            }
                            Text("Límite de esta app", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("$used de $maximum consultas usadas", fontWeight = FontWeight.Bold)
                    Surface(
                        modifier = Modifier.clickable(onClick = { showEditor = true }).secondaryButtonTarget(),
                        color = FlightAppColors.mintHeader,
                        shape = CircleShape,
                    ) {
                        Text("Reducir", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = FlightAppColors.tealDark, fontWeight = FontWeight.Bold)
                    }
                }
                LinearProgressIndicator(
                    progress = { if (maximum <= 0) 0f else (used.toFloat() / maximum).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = FlightAppColors.teal,
                    trackColor = Color(0xFFDCE6EF),
                )
                Text("$remaining disponibles · ventana de 30 días", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                Surface(color = FlightAppColors.paleYellow, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppIcon(AppIcon.Info, null, modifier = Modifier.size(18.dp), tint = Color(0xFFE2A52F))
                        Text("El saldo se consulta por separado para cada cuenta. El límite local de esta app es independiente; las reservas ya iniciadas se conservan.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF75602E))
                    }
                }
            }
        }
    }
    if (showEditor) AlertDialog(
        onDismissRequest = { showEditor = false },
        title = { Text("Reducir presupuesto local") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Solo puedes reducir el máximo actual de $maximum GET.")
                OutlinedTextField(draft, { draft = it.filter(Char::isDigit).take(3) }, label = { Text("Nuevo máximo") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(onClick = { draft.toIntOrNull()?.let(onSave); showEditor = false }, enabled = draft.toIntOrNull()?.let { it in 1 until maximum } == true, modifier = Modifier.secondaryButtonTarget()) { Text("Guardar límite") }
        },
        dismissButton = { TextButton(onClick = { showEditor = false }, modifier = Modifier.secondaryButtonTarget()) { Text("Cancelar") } },
    )
}

@Composable
private fun DateButton(label: String, date: LocalDate, modifier: Modifier, onClick: (LocalDate) -> Unit) {
    OutlinedButton(onClick = { onClick(date) }, modifier = modifier.primaryButtonTarget()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(AppIcon.Calendar, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Column(horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO")).format(date), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun OutcomeCard(outcome: ProbeOutcome) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resultado: ${outcome.statusLabel}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(outcome.detail)
            Text("Criterios: CTG → CLO ${outcome.criteria.outboundDate}; regreso ${outcome.criteria.returnDate}; ${if (outcome.criteria.directOnly) "directos" else "con escalas permitidas"}.")
            Text("Saldo consultado antes de esta tanda: ${outcome.accountAtStart.totalSearchesLeft ?: "No disponible"}; comprobación ${formatInstant(outcome.accountAtStart.checkedAt)}.")
            Text("GET de vuelos iniciados en esta prueba: ${outcome.flightGetsStarted} de 2.")
            Text("Respuesta observada: ${formatInstant(outcome.completedAt)}")
            HorizontalDivider()
            outcome.outbound?.let { OptionDetails("Salida seleccionada", it) }
            outcome.selectedReturn?.let { OptionDetails("Regreso asociado", it) }
            if (outcome.verifiedRoundTripPriceCop != null) {
                Text("Tarifa total de ida y vuelta observada: ${formatCop(outcome.verifiedRoundTripPriceCop)}. No se suman los importes de las etapas.", fontWeight = FontWeight.SemiBold)
            } else if (outcome.selectedReturn != null) {
                Text("Total de ida y vuelta: no verificado. No se suman importes de las etapas.", fontWeight = FontWeight.SemiBold)
            }
            Text("Aerolíneas devueltas por la fuente (${outcome.airlinesDetected.size}):", fontWeight = FontWeight.SemiBold)
            if (outcome.airlinesDetected.isEmpty()) Text("No se recibieron nombres de operadores.")
            outcome.airlinesDetected.forEach { airline -> AirlineIdentity(airline, Modifier.fillMaxWidth(), compact = true) }
            if (outcome.malformedOptions > 0) {
                Text(
                    "Opciones no interpretadas: ${outcome.malformedOptions}. La lista de operadores no confirma cobertura de esas opciones.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                if (outcome.wingoDetected) "Wingo: detectada en las opciones recibidas; esto no certifica una tarifa completa de Wingo."
                else "Wingo: no detectada en las opciones devueltas para estos criterios; no demuestra ausencia del mercado.",
                style = MaterialTheme.typography.bodySmall,
            )
            outcome.googleFlightsUrl?.let { url ->
                Text("El enlace abre Google Flights para revisar la búsqueda; no garantiza conservar esta tarifa.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                }, modifier = Modifier.primaryButtonTarget()) { Text("Abrir Google Flights") }
            }
        }
    }
}

@Composable
private fun OptionDetails(title: String, option: FlightOption) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        val first = option.segments.firstOrNull()
        val last = option.segments.lastOrNull()
        Text("${first?.departure?.code ?: "?"} → ${last?.arrival?.code ?: "?"} · ${option.carriers.joinToString(" + ").ifBlank { "Operador no informado" }} · ${option.segments.size - 1} escala(s)")
        option.segments.forEachIndexed { index, segment ->
            val departure = FlightDepartureTime.format(segment.departure.localDateTime?.toString()) ?: "hora no informada"
            Text(
                "${if (option.segments.size > 1) "Tramo ${index + 1} · " else "Salida · "}${segment.departure.code} $departure",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        option.priceCop?.let { amount ->
            Text("Importe devuelto por esta opción: ${formatCop(amount)} (no equivale por sí solo al total de la reserva).", style = MaterialTheme.typography.bodySmall)
        }
        if (option.priceCop == null) Text("Importe no disponible o no válido.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(AppIcon.Info, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) { Text(message, modifier = Modifier.padding(16.dp)) }
}

private fun formatCop(amount: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).apply {
    currency = Currency.getInstance("COP")
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}.format(amount)

private fun formatInstant(value: Instant): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'COT'")
    .withZone(ZoneId.of("America/Bogota"))
    .format(value)
