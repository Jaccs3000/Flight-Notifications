package com.flightnotifications.app.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightnotifications.app.R
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.ui.theme.FlightColors

private const val DEST_ALERTS = "alerts"
private const val DEST_ACTIVITY = "activity"
private const val DEST_SETTINGS = "settings"
private const val DEST_EDITOR = "editor"
private const val DEST_DETAIL = "detail"
private const val DEST_DIAGNOSTICS = "diagnostics"

@Composable
fun ProbeScreen(viewModel: ProbeViewModel, alertsViewModel: FlightAlertsViewModel? = null) {
    if (alertsViewModel == null) return
    FlightAppNavigation(viewModel, alertsViewModel)
}

@Composable
private fun FlightAppNavigation(probeViewModel: ProbeViewModel, alertsViewModel: FlightAlertsViewModel) {
    val probe by probeViewModel.state.collectAsStateWithLifecycle()
    val alerts by alertsViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var destination by rememberSaveable { mutableStateOf(DEST_ALERTS) }
    var primaryDestination by rememberSaveable { mutableStateOf(DEST_ALERTS) }
    var secondaryReturnDestination by rememberSaveable { mutableStateOf(DEST_ALERTS) }
    var detailReturnDestination by rememberSaveable { mutableStateOf(DEST_ALERTS) }
    var selectedAlertId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedNotificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingAlertId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var showCredentialDelete by rememberSaveable { mutableStateOf(false) }
    var pendingEnableAlertId by rememberSaveable { mutableStateOf<String?>(null) }
    var notificationsAllowed by rememberSaveable { mutableStateOf(alertsViewModel.notificationsAllowed()) }
    val screenStateHolder = rememberSaveableStateHolder()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = alertsViewModel.notificationsAllowed()
        val pending = pendingEnableAlertId
        pendingEnableAlertId = null
        if (granted && pending != null) alertsViewModel.setEnabled(pending, true)
        else if (!granted) alertsViewModel.notificationPermissionDenied()
    }

    DisposableEffect(lifecycleOwner, alertsViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = alertsViewModel.notificationsAllowed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(alerts.notificationOpenSequence) {
        if (alerts.notificationOpenSequence > 0 && alerts.selectedAlertId != null) {
            selectedAlertId = alerts.selectedAlertId
            selectedNotificationId = null
            detailReturnDestination = DEST_ALERTS
            primaryDestination = DEST_ALERTS
            destination = DEST_DETAIL
        }
    }

    fun navigateToPrimary(target: String) {
        destination = target
        primaryDestination = target
        selectedAlertId = null
        selectedNotificationId = null
        editingAlertId = null
        if (alerts.selectedAlertId != null) alertsViewModel.clearSelectedAlert()
    }

    fun openDetail(id: String, from: String = primaryDestination, notificationId: String? = null) {
        selectedAlertId = id
        selectedNotificationId = notificationId
        detailReturnDestination = from
        destination = DEST_DETAIL
        if (alerts.selectedAlertId != null) alertsViewModel.clearSelectedAlert()
    }

    fun openEditor(id: String?) {
        editingAlertId = id
        secondaryReturnDestination = if (destination == DEST_DETAIL) DEST_DETAIL else DEST_ALERTS
        destination = DEST_EDITOR
        alertsViewModel.clearFeedback()
    }

    fun goBack() {
        destination = when (destination) {
            DEST_DETAIL -> detailReturnDestination
            DEST_EDITOR, DEST_DIAGNOSTICS -> secondaryReturnDestination
            else -> DEST_ALERTS
        }
        if (destination == DEST_DETAIL && alerts.selectedAlertId != null) alertsViewModel.clearSelectedAlert()
        if (destination != DEST_DETAIL) {
            selectedAlertId = null
            selectedNotificationId = null
        }
        editingAlertId = null
    }

    BackHandler(enabled = destination !in setOf(DEST_ALERTS, DEST_ACTIVITY, DEST_SETTINGS)) { goBack() }

    val isPrimary = destination in setOf(DEST_ALERTS, DEST_ACTIVITY, DEST_SETTINGS)
    Scaffold(
        containerColor = FlightColors.Background,
        bottomBar = {
            if (isPrimary) {
                NavigationBar(
                    containerColor = FlightColors.Surface,
                    tonalElevation = 1.dp,
                ) {
                    NavigationBarItem(
                        selected = destination == DEST_ALERTS,
                        onClick = { navigateToPrimary(DEST_ALERTS) },
                        icon = { Icon(painterResource(R.drawable.ic_nav_alerts), contentDescription = null) },
                        label = { Text("Alertas") },
                        modifier = Modifier.testTag("nav-alerts"),
                    )
                    NavigationBarItem(
                        selected = destination == DEST_ACTIVITY,
                        onClick = { navigateToPrimary(DEST_ACTIVITY) },
                        icon = { Icon(painterResource(R.drawable.ic_nav_activity), contentDescription = null) },
                        label = { Text("Actividad") },
                        modifier = Modifier.testTag("nav-activity"),
                    )
                    NavigationBarItem(
                        selected = destination == DEST_SETTINGS,
                        onClick = { navigateToPrimary(DEST_SETTINGS) },
                        icon = { Icon(painterResource(R.drawable.ic_nav_settings), contentDescription = null) },
                        label = { Text("Ajustes") },
                        modifier = Modifier.testTag("nav-settings"),
                    )
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            val stateKey = when (destination) {
                DEST_DETAIL -> "$destination:${selectedAlertId.orEmpty()}"
                DEST_EDITOR -> "$destination:${editingAlertId ?: "new"}"
                else -> destination
            }
            screenStateHolder.SaveableStateProvider(stateKey) {
                when (destination) {
                DEST_ALERTS -> AlertsHomeScreen(
                    state = alerts,
                    airportCatalog = alertsViewModel.airportCatalog,
                    notificationsAllowed = notificationsAllowed,
                    onOpen = { openDetail(it) },
                    onNew = { openEditor(null) },
                    onToggle = { id, enable ->
                        if (!enable) alertsViewModel.setEnabled(id, false)
                        else if (alertsViewModel.notificationsAllowed()) alertsViewModel.setEnabled(id, true)
                        else if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            pendingEnableAlertId = id
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                            alertsViewModel.notificationPermissionDenied()
                        }
                    },
                    onOpenNotificationSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )

                DEST_ACTIVITY -> ActivityScreen(
                    state = alerts,
                    airportCatalog = alertsViewModel.airportCatalog,
                    onOpenAlert = { id, notificationId -> openDetail(id, DEST_ACTIVITY, notificationId) },
                    onClear = alertsViewModel::requestClearNotifications,
                )

                DEST_SETTINGS -> SettingsScreen(
                    probeState = probe,
                    notificationsAllowed = notificationsAllowed,
                    versionName = appVersionName(),
                    onSaveCredential = probeViewModel::saveCredential,
                    onDeleteCredential = { showCredentialDelete = true },
                    onCheckAccount = probeViewModel::checkAccount,
                    onBudgetLimit = probeViewModel::reduceLocalBudgetLimit,
                    onOpenDiagnostics = {
                        secondaryReturnDestination = DEST_SETTINGS
                        destination = DEST_DIAGNOSTICS
                    },
                    onOpenNotificationSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                    onCatalog = { showCatalog = true },
                )

                DEST_EDITOR -> {
                    val target = editingAlertId?.let { id -> alerts.alerts.firstOrNull { it.alert.id == id }?.alert }
                    AlertEditorScreen(
                        alert = target,
                        error = alerts.error,
                        airportCatalog = alertsViewModel.airportCatalog,
                        onBack = ::goBack,
                        onFormChanged = alertsViewModel::clearFeedback,
                        onSave = { input ->
                            alertsViewModel.save(input) {
                                destination = secondaryReturnDestination
                                editingAlertId = null
                            }
                        },
                    )
                }

                DEST_DETAIL -> AlertDetailScreen(
                    alertId = selectedAlertId,
                    notification = selectedNotificationId?.let { id -> alerts.notifications.firstOrNull { it.id == id } },
                    state = alerts,
                    airportCatalog = alertsViewModel.airportCatalog,
                    onBack = ::goBack,
                    onEdit = { openEditor(it) },
                    onDelete = { alertsViewModel.requestDelete(it) },
                    onSearch = alertsViewModel::requestReview,
                )

                DEST_DIAGNOSTICS -> DiagnosticsScreen(
                    probeState = probe,
                    schedulingStatus = alerts.schedulingStatus,
                    alertsState = alerts,
                    onBack = ::goBack,
                    onCheckScheduling = alertsViewModel::checkScheduling,
                    onOutboundDate = probeViewModel::setOutboundDate,
                    onReturnDate = probeViewModel::setReturnDate,
                    onDirectOnly = probeViewModel::setDirectOnly,
                    onStartProbe = probeViewModel::requestFlightProbeConfirmation,
                    onConfirmProbe = probeViewModel::confirmFlightProbe,
                    onDismissProbe = probeViewModel::dismissFlightProbeConfirmation,
                    onCancelProbe = probeViewModel::cancelFlightProbe,
                    onClearConnectionLog = probeViewModel::clearDiagnosticLog,
                    onClearFlightLog = probeViewModel::clearFlightDiagnosticLog,
                )

                    else -> navigateToPrimary(DEST_ALERTS)
                }
            }
        }
    }

    alerts.reviewConfirmationId?.let { id ->
        val alert = alerts.alerts.firstOrNull { it.alert.id == id }?.alert
        if (alert != null) {
            AlertDialog(
                onDismissRequest = alertsViewModel::dismissReviewConfirmation,
                title = { Text("Revisar precios ahora") },
                text = {
                    Text(
                        "${alert.originIata} ↔ ${alert.destinationIata}, ${alert.outboundDate} / ${alert.returnDate}. " +
                            "Se comprobará la cuenta una vez y, si hay capacidad, se iniciarán hasta 4 GET de vuelos (1 inicial + hasta 3 regresos asociados). " +
                            "Cancelar inicia 0 GET de vuelos. La búsqueda manual no activa una alerta pausada.",
                    )
                },
                confirmButton = { TextButton(onClick = alertsViewModel::confirmReview) { Text("Continuar") } },
                dismissButton = { TextButton(onClick = alertsViewModel::dismissReviewConfirmation) { Text("Cancelar") } },
            )
        }
    }

    alerts.deletingAlertId?.let {
        AlertDialog(
            onDismissRequest = alertsViewModel::dismissDelete,
            title = { Text("Eliminar alerta") },
            text = { Text("Se eliminarán los resultados y avisos de esta alerta. El presupuesto global, la clave y las demás alertas se conservan.") },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedAlertId == it) goBack()
                    alertsViewModel.confirmDelete()
                }, modifier = Modifier.testTag("confirm-delete-alert")) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = alertsViewModel::dismissDelete) { Text("Cancelar") } },
        )
    }

    if (alerts.showClearNotificationsConfirmation) {
        AlertDialog(
            onDismissRequest = alertsViewModel::dismissClearNotifications,
            title = { Text("Limpiar avisos") },
            text = { Text("Se borrará el historial visible. La tarifa más baja podrá notificarse otra vez en una revisión futura y la cuota no cambiará.") },
            confirmButton = { TextButton(onClick = alertsViewModel::confirmClearNotifications) { Text("Limpiar") } },
            dismissButton = { TextButton(onClick = alertsViewModel::dismissClearNotifications) { Text("Cancelar") } },
        )
    }

    if (showCredentialDelete) {
        AlertDialog(
            onDismissRequest = { showCredentialDelete = false },
            title = { Text("Eliminar clave") },
            text = { Text("Se borrará la clave cifrada de este dispositivo y se invalidarán el saldo y los resultados en memoria.") },
            confirmButton = {
                TextButton(onClick = { showCredentialDelete = false; probeViewModel.deleteCredential() }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { showCredentialDelete = false }) { Text("Volver") } },
        )
    }

    if (showCatalog) {
        AirlineCatalogDialog(alertsViewModel) { showCatalog = false }
    }
}

@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return rememberPackageVersion(context)
}

@Composable
private fun rememberPackageVersion(context: android.content.Context): String {
    return androidx.compose.runtime.remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
}
