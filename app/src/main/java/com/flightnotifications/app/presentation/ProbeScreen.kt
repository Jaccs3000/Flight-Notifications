package com.flightnotifications.app.presentation

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flightnotifications.app.domain.model.AccountSnapshot
import com.flightnotifications.app.domain.model.FlightOption
import com.flightnotifications.app.domain.model.ProbeOutcome
import com.flightnotifications.app.domain.model.SearchCriteria
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private enum class AppPage { ALERTS, ACTIVITY, SETTINGS, DIAGNOSTIC, EDITOR, DETAIL }

private val AppPage.isPrimary: Boolean
    get() = this == AppPage.ALERTS || this == AppPage.ACTIVITY || this == AppPage.SETTINGS

@Composable
fun ProbeScreen(viewModel: ProbeViewModel, alertsViewModel: FlightAlertsViewModel? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf(false) }
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

    fun openSubpage(next: AppPage) {
        returnPage = page.takeIf { it.isPrimary } ?: returnPage
        page = next
    }

    fun goBack() {
        page = returnPage.takeIf { it.isPrimary } ?: AppPage.ALERTS
    }

    BackHandler(enabled = !page.isPrimary) { goBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (page == AppPage.ALERTS && alertsViewModel != null) {
                ExtendedFloatingActionButton(
                    onClick = { editorAlertId = null; openSubpage(AppPage.EDITOR) },
                    icon = { Text("＋") },
                    text = { Text("Nueva alerta") },
                    modifier = Modifier.testTag("new-alert").primaryButtonTarget(),
                )
            }
        },
        bottomBar = {
            if (page.isPrimary) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(
                        Triple(AppPage.ALERTS, "✈", "Alertas"),
                        Triple(AppPage.ACTIVITY, "◷", "Actividad"),
                        Triple(AppPage.SETTINGS, "⚙", "Ajustes"),
                    ).forEach { (destination, icon, label) ->
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = { page = destination },
                            icon = { Text(icon, style = MaterialTheme.typography.titleMedium) },
                            label = { Text(label) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { insets ->
        when (page) {
            AppPage.ALERTS -> Column(
                modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp).padding(bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (alertsViewModel != null) {
                    FlightAlertsSection(
                        viewModel = alertsViewModel,
                        maximumLocalGets = state.localBudgetLimit,
                        onEditAlert = { id -> editorAlertId = id; openSubpage(AppPage.EDITOR) },
                        onOpenDetail = { id -> selectedAlertId = id; openSubpage(AppPage.DETAIL) },
                    )
                } else EmptyAppCard("No se pudo cargar el administrador de alertas.")
            }
            AppPage.ACTIVITY -> Column(
                modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ActivityScreen(
                    state = alertsState,
                    onClear = { alertsViewModel?.requestClearNotifications() },
                    onOpenAlert = { id -> selectedAlertId = id; openSubpage(AppPage.DETAIL) },
                )
            }
            AppPage.SETTINGS -> Column(
                modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingsScreen(
                    state = state,
                    credentialBusy = state.isRunningProbe || state.isCheckingAccount || state.isDeletingCredential,
                    alertsViewModel = alertsViewModel,
                    notificationsAllowed = notificationsAllowed,
                    onSaveCredential = viewModel::saveCredential,
                    onDeleteCredential = { pendingDelete = true },
                    onCheckAccount = viewModel::checkAccount,
                    onReduceBudget = viewModel::reduceLocalBudgetLimit,
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

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("Eliminar clave") },
            text = { Text("Se borrará la clave cifrada de este dispositivo y se invalidarán el saldo y los resultados en memoria.") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = false; viewModel.deleteCredential() }, modifier = Modifier.secondaryButtonTarget()) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = false }, modifier = Modifier.secondaryButtonTarget()) { Text("Volver") } },
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
                    alertsViewModel.airlineCatalog.airlines.forEach { Text("• ${it.name} (${it.code})") }
                    Text(alertsViewModel.airlineCatalog.source, style = MaterialTheme.typography.bodySmall)
                    Text("Catálogo informativo, no exhaustivo. No filtra la búsqueda ni garantiza tarifas o cobertura.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showCatalog = false }, modifier = Modifier.secondaryButtonTarget()) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun ActivityScreen(state: FlightAlertsUiState, onClear: () -> Unit, onOpenAlert: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Actividad", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Avisos enviados", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
            TextButton(onClick = onClear, enabled = state.notifications.isNotEmpty(), modifier = Modifier.secondaryButtonTarget()) { Text("Limpiar") }
        }
        Text("La tarifa más baja de cada revisión. La misma tarifa puede aparecer en revisiones distintas.", style = MaterialTheme.typography.bodyMedium)
        if (state.notifications.isEmpty()) {
            EmptyAppCard("Aún no se han enviado avisos. Cuando una revisión encuentre una tarifa elegible, aparecerá aquí.")
        } else {
            state.notifications.forEach { notice ->
                val alert = state.alerts.firstOrNull { it.alert.id == notice.alertId }?.alert
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = { onOpenAlert(notice.alertId) }),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                            Text("✓  Tarifa verificada", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(notice.routeSummary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(alert?.let { "${formatTripDates(it.outboundDate, it.returnDate)} · 1 adulto" } ?: "1 adulto", color = MaterialTheme.colorScheme.secondary)
                        Text(formatCop(notice.priceCop), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("COP · ida y vuelta", color = MaterialTheme.colorScheme.secondary)
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("${notice.outboundCarrier} / ${notice.returnCarrier}", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                            Text(formatShortTime(notice.sentAt), color = MaterialTheme.colorScheme.secondary)
                            Text("  ›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            Text("Hasta 100 avisos locales. Limpiar cierra también las notificaciones visibles del sistema.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
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
    onDeleteCredential: () -> Unit,
    onCheckAccount: () -> Unit,
    onReduceBudget: (Int) -> Unit,
    onOpenDiagnostic: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("SerpApi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        CredentialCard(
            configured = state.credentialConfigured,
            busy = credentialBusy,
            accountMessage = state.accountMessage,
            accountActive = state.account?.accountStatus.equals("Active", ignoreCase = true),
            onSave = onSaveCredential,
            onDelete = onDeleteCredential,
            onCheckAccount = onCheckAccount,
        )
        Text("Consultas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        LocalBudgetCard(
            maximum = state.localBudgetLimit,
            used = state.budget?.usedInWindow ?: 0,
            remaining = state.budget?.remainingInWindow ?: state.localBudgetLimit,
            onSave = onReduceBudget,
            officialAvailable = state.account?.conservativeSearchesLeft,
            accountCheckedAt = state.account?.checkedAt?.toEpochMilli(),
        )
        Text("El límite local es independiente del saldo de SerpApi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column {
                SettingsRow("♧", "Notificaciones", if (notificationsAllowed) "Permitidas" else "Desactivadas", onOpenNotificationSettings, success = notificationsAllowed)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SettingsRow("⌁", "Diagnóstico", "Pruebas y registros", onOpenDiagnostic)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SettingsRow("▤", "Catálogo de aerolíneas", null, onOpenCatalog)
            }
        }
        Text("Alertas de vuelos · versión 1.0.6", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        state.errorMessage?.let { ErrorCard(it) }
        if (alertsViewModel == null) Text("No se pudo cargar el estado de alertas.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SettingsRow(icon: String, title: String, subtitle: String?, onClick: () -> Unit, success: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(icon, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = if (success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
        }
        Text("›", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.headlineSmall)
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
            TextButton(onClick = onBack, modifier = Modifier.secondaryButtonTarget()) { Text("‹") }
            Column {
                Text("Diagnóstico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Revisa el funcionamiento de la app.", color = MaterialTheme.colorScheme.secondary)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Programación automática", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (schedulingStatus.contains("Enqueued", true) || schedulingStatus.contains("program", true)) "● Programada" else "● Estado", color = MaterialTheme.colorScheme.tertiary)
                }
                Text(schedulingStatus, color = MaterialTheme.colorScheme.secondary)
                HorizontalDivider()
                Text("Android puede aplazar las revisiones por batería, Doze, conectividad o detención forzosa.", color = MaterialTheme.colorScheme.secondary)
                OutlinedButton(onClick = onCheckSchedule, modifier = Modifier.fillMaxWidth().primaryButtonTarget()) { Text("Comprobar programación") }
                Text("No consume consultas de vuelos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { showFlightProbe = !showFlightProbe },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Prueba manual de vuelos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("CTG ↔ CLO · Fechas y vuelos directos", color = MaterialTheme.colorScheme.secondary)
                    }
                    Text(if (showFlightProbe) "⌃" else "⌄", color = MaterialTheme.colorScheme.secondary)
                }
                if (showFlightProbe) {
                    SearchCard(state, onOutboundDate, onReturnDate, onDirectOnly, onStartProbe, onCancelProbe)
                    state.errorMessage?.let { ErrorCard(it) }
                    state.outcome?.let { OutcomeCard(it) }
                }
            }
        }
        Text("Registro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showFlightLog, onClick = { showFlightLog = false }, label = { Text("Conexión") }, modifier = Modifier.chipTarget())
            FilterChip(selected = showFlightLog, onClick = { showFlightLog = true }, label = { Text("Vuelos") }, modifier = Modifier.chipTarget())
        }
        DiagnosticLogCard(
            title = if (showFlightLog) "Registro de búsqueda de vuelos" else "Registro de conexión",
            emptyMessage = if (showFlightLog) "Aún no hay eventos. Confirma una búsqueda para registrar el intento." else "Aún no hay eventos. Pulsa Probar conexión para registrar el intento.",
            logText = if (showFlightLog) state.flightDiagnosticLog else state.accountDiagnosticLog,
            onClear = if (showFlightLog) onClearFlightLog else onClearAccountLog,
            onCopy = if (showFlightLog) onCopyFlightLog else onCopyAccountLog,
        )
        Text("Los registros no contienen API keys, tokens ni cuerpos JSON. Las pruebas manuales de vuelos pueden consumir cuota.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun EmptyAppCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.secondary)
    }
}

private fun formatTripDates(outbound: String, returning: String): String {
    val format = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))
    val start = LocalDate.parse(outbound)
    val end = LocalDate.parse(returning)
    return if (start.month == end.month && start.year == end.year) "${start.dayOfMonth}–${end.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO")))}"
    else "${start.format(format)}–${end.format(format)}"
}

private fun formatShortTime(epochMillis: Long): String = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.of("America/Bogota")).format(Instant.ofEpochMilli(epochMillis))

@Composable
private fun DiagnosticLogCard(
    title: String,
    emptyMessage: String,
    logText: String,
    onClear: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Registro local para investigar. No guarda la API key, tokens ni el contenido de las respuestas.",
                style = MaterialTheme.typography.bodySmall,
            )
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
                OutlinedButton(onClick = onCopy, enabled = logText.isNotBlank(), modifier = Modifier.primaryButtonTarget()) { Text("Copiar") }
                OutlinedButton(onClick = onClear, enabled = logText.isNotBlank(), modifier = Modifier.primaryButtonTarget()) { Text("Limpiar") }
            }
        }
    }
}

@Composable
private fun CredentialCard(
    configured: Boolean,
    busy: Boolean,
    accountMessage: String?,
    accountActive: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCheckAccount: () -> Unit,
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var editing by remember(configured) { mutableStateOf(!configured) }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("♢", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (configured) "Clave guardada" else "Clave de SerpApi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (configured) "Protegida en este dispositivo" else "Guárdala cifrada para conectar la app", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                }
                if (configured) TextButton(onClick = { editing = !editing }, modifier = Modifier.secondaryButtonTarget()) { Text(if (editing) "Cerrar" else "Cambiar") }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (accountActive) "✓" else "○", style = MaterialTheme.typography.headlineSmall, color = if (accountActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (accountActive) "Cuenta activa" else if (configured) "Cuenta sin comprobar" else "Cuenta no conectada", color = if (accountActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(accountMessage ?: if (accountActive) "Saldo consultado con SerpApi." else "Comprueba el estado de la cuenta antes de buscar.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onCheckAccount, enabled = configured && !busy, modifier = Modifier.padding(top = 5.dp).primaryButtonTarget()) { Text("Probar conexión") }
                }
            }
            if (editing) {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Clave privada") },
                    placeholder = { Text(if (configured) "Escribe una clave nueva para sustituirla" else "Ingresa la clave en este dispositivo") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !busy,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onSave(apiKeyInput); apiKeyInput = "" }, enabled = !busy && apiKeyInput.isNotBlank(), modifier = Modifier.primaryButtonTarget()) {
                        Text(if (configured) "Guardar cambios" else "Guardar cifrada")
                    }
                    if (configured) TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.secondaryButtonTarget()) { Text("Eliminar clave") }
                }
                Text("La clave se cifra con Android Keystore y no se muestra después de guardarla.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun AccountCard(account: AccountSnapshot, message: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ensayo CTG ↔ CLO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DateButton("Ida", state.outboundDate, Modifier.weight(1f)) { current ->
                    val picker = DatePickerDialog(context, { _, year, month, day ->
                        val selected = LocalDate.of(year, month + 1, day)
                        onOutboundDate(selected)
                        val returnStart = if (state.returnDate.isAfter(selected)) state.returnDate else selected.plusDays(1)
                        Handler(Looper.getMainLooper()).post {
                            val returnPicker = DatePickerDialog(context, { _, returnYear, returnMonth, returnDay -> onReturnDate(LocalDate.of(returnYear, returnMonth + 1, returnDay)) }, returnStart.year, returnStart.monthValue - 1, returnStart.dayOfMonth)
                            returnPicker.datePicker.minDate = selected.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            returnPicker.show()
                        }
                    }, current.year, current.monthValue - 1, current.dayOfMonth)
                    picker.datePicker.minDate = LocalDate.now(SearchCriteria.COLOMBIA_ZONE)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    picker.show()
                }
            DateButton("Regreso", state.returnDate, Modifier.weight(1f)) { current ->
                    val picker = DatePickerDialog(context, { _, year, month, day -> onReturnDate(LocalDate.of(year, month + 1, day)) }, current.year, current.monthValue - 1, current.dayOfMonth)
                    picker.datePicker.minDate = state.outboundDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    picker.show()
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
            Text("1 adulto · Económica · Ida y vuelta · COP · Aerolíneas: todas", style = MaterialTheme.typography.bodyMedium)
            Text("No se envían filtros de compañías ni precio máximo.", style = MaterialTheme.typography.bodySmall)
            state.budget?.let {
                Text("Presupuesto local: ${it.usedInWindow}/${it.maximumRequests} GET · ${it.remainingInWindow} disponibles")
                Text(
                    "Ventana conservadora de 30 días; el tiempo con el teléfono apagado no adelanta el vencimiento del contador.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
                ) { Text("Probar vuelo de ida y vuelta") }
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
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Disponibles en SerpApi", style = MaterialTheme.typography.titleMedium)
            Text(officialAvailable?.toString() ?: "—", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(accountCheckedAt?.let { "Saldo consultado ${formatShortTime(it)}" } ?: "Comprueba la cuenta para consultar el saldo.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Presupuesto local", fontWeight = FontWeight.SemiBold)
                Text("$used de $maximum")
            }
            LinearProgressIndicator(
                progress = { if (maximum <= 0) 0f else (used.toFloat() / maximum).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("$remaining disponibles", color = MaterialTheme.colorScheme.secondary)
                TextButton(onClick = { showEditor = true }, modifier = Modifier.secondaryButtonTarget()) { Text("Reducir límite") }
            }
            Text("Ventana local conservadora de 30 días. Las reservas ya iniciadas se conservan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(date.toString(), style = MaterialTheme.typography.bodyMedium)
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
            outcome.airlinesDetected.forEach { airline -> Text("• $airline") }
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
        option.priceCop?.let { amount ->
            Text("Importe devuelto por esta opción: ${formatCop(amount)} (no equivale por sí solo al total de la reserva).", style = MaterialTheme.typography.bodySmall)
        }
        if (option.priceCop == null) Text("Importe no disponible o no válido.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
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
