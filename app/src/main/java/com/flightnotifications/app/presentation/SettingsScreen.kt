package com.flightnotifications.app.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.flightnotifications.app.R
import com.flightnotifications.app.domain.model.AccountSnapshot
import com.flightnotifications.app.domain.model.LocalBudgetSnapshot
import com.flightnotifications.app.ui.theme.FlightColors

@Composable
internal fun SettingsScreen(
    probeState: ProbeUiState,
    notificationsAllowed: Boolean,
    versionName: String,
    onSaveCredential: (String) -> Unit,
    onDeleteCredential: () -> Unit,
    onCheckAccount: () -> Unit,
    onBudgetLimit: (Int) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onCatalog: () -> Unit,
) {
    var showCredentialDialog by rememberSaveable { mutableStateOf(false) }
    var newCredential by remember { mutableStateOf("") }
    var showBudgetDialog by rememberSaveable { mutableStateOf(false) }
    var budgetDraft by rememberSaveable(probeState.localBudgetLimit) { mutableStateOf(probeState.localBudgetLimit.toString()) }
    var showAccountDetails by rememberSaveable { mutableStateOf(false) }

    AppPage {
        SectionHeading("Ajustes")
        Text("SerpApi", style = MaterialTheme.typography.titleLarge, color = FlightColors.SecondaryText)

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VectorGlyph(R.drawable.ic_security, tint = FlightColors.Primary, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (probeState.credentialConfigured) "Clave guardada" else "Clave no configurada", style = MaterialTheme.typography.titleMedium)
                    Text(if (probeState.credentialConfigured) "Protegida en este dispositivo" else "Se cifra con Android Keystore", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
                }
                if (probeState.credentialConfigured) {
                    TextButton(onClick = { showCredentialDialog = true }, modifier = Modifier.testTag("change-serpapi-key")) { Text("Cambiar") }
                }
            }
            if (!probeState.credentialConfigured) {
                OutlinedButton(onClick = { showCredentialDialog = true }, modifier = Modifier.testTag("add-serpapi-key")) { Text("Añadir clave") }
            } else {
                HorizontalDivider(color = FlightColors.Outline)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    VectorGlyph(
                        if (probeState.account?.accountStatus.equals("Active", ignoreCase = true)) R.drawable.ic_check_circle else R.drawable.ic_info,
                        tint = if (probeState.account?.accountStatus.equals("Active", ignoreCase = true)) FlightColors.Success else FlightColors.SecondaryText,
                        modifier = Modifier.size(30.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        val status = when {
                            probeState.account == null && probeState.isCheckingAccount -> "Comprobando cuenta…"
                            probeState.account == null -> "Cuenta sin comprobar"
                            probeState.account.accountStatus.equals("Active", ignoreCase = true) -> "Cuenta activa"
                            else -> "Cuenta ${probeState.account.accountStatus}"
                        }
                        Text(status, style = MaterialTheme.typography.titleMedium, color = if (probeState.account?.accountStatus.equals("Active", ignoreCase = true)) FlightColors.Success else FlightColors.OnSurface)
                        probeState.account?.let { Text("Comprobada ${formatLocalTime(it.checkedAt.toEpochMilli())}", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText) }
                    }
                    if (probeState.isCheckingAccount) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onCheckAccount, enabled = !probeState.isCheckingAccount && !probeState.isRunningProbe) { Text("Probar conexión") }
                    TextButton(onClick = onDeleteCredential, enabled = !probeState.isCheckingAccount && !probeState.isRunningProbe) { Text("Eliminar clave") }
                }
            }
        }

        probeState.accountMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText) }
        probeState.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }

        Text("Consultas", style = MaterialTheme.typography.titleLarge, color = FlightColors.SecondaryText)
        AccountAndBudgetCard(
            account = probeState.account,
            budget = probeState.budget,
            limit = probeState.localBudgetLimit,
            onReduce = { budgetDraft = probeState.localBudgetLimit.toString(); showBudgetDialog = true },
            expanded = showAccountDetails,
            onExpand = { showAccountDetails = !showAccountDetails },
        )
        Text("El límite local es independiente del saldo de SerpApi.", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)

        AppCard(contentPadding = 8.dp) {
            SettingsNavigationRow("Notificaciones", if (notificationsAllowed) "Permitidas" else "Desactivadas", R.drawable.ic_nav_alerts, onOpenNotificationSettings, positive = notificationsAllowed)
            SettingsNavigationRow("Diagnóstico", "Pruebas y registros", R.drawable.ic_diagnostics, onOpenDiagnostics, testTag = "open-diagnostics")
            SettingsNavigationRow("Catálogo de aerolíneas", "Información de referencia", R.drawable.ic_book, onCatalog, testTag = "open-catalog")
        }
        Text("Versión $versionName", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }

    if (showBudgetDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Reducir presupuesto local") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("El máximo sólo se puede reducir. El consumo y las reservas existentes se conservan.")
                    OutlinedTextField(
                        value = budgetDraft,
                        onValueChange = { budgetDraft = it.filter(Char::isDigit).take(3) },
                        label = { Text("Nuevo máximo de GET") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Text("Actualmente: ${probeState.budget?.usedInWindow ?: 0} / ${probeState.localBudgetLimit} iniciados.", style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        budgetDraft.toIntOrNull()?.let(onBudgetLimit)
                        showBudgetDialog = false
                    },
                    enabled = budgetDraft.toIntOrNull()?.let { it in 1 until probeState.localBudgetLimit } == true,
                ) { Text("Reducir") }
            },
            dismissButton = { TextButton(onClick = { showBudgetDialog = false }) { Text("Cancelar") } },
        )
    }

    if (showCredentialDialog) {
        AlertDialog(
            onDismissRequest = { showCredentialDialog = false; newCredential = "" },
            title = { Text(if (probeState.credentialConfigured) "Cambiar clave de SerpApi" else "Conectar SerpApi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("La clave se cifra y se guarda en este dispositivo. No se vuelve a mostrar ni se incluye en los registros.")
                    OutlinedTextField(
                        value = newCredential,
                        onValueChange = { newCredential = it },
                        label = { Text("Clave privada") },
                        placeholder = { Text("Pega tu clave de SerpApi") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !probeState.isCheckingAccount && !probeState.isRunningProbe,
                        modifier = Modifier.fillMaxWidth().testTag("serpapi-key-dialog-field"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveCredential(newCredential)
                        newCredential = ""
                        showCredentialDialog = false
                    },
                    enabled = newCredential.isNotBlank() && !probeState.isCheckingAccount && !probeState.isRunningProbe,
                    modifier = Modifier.testTag("save-serpapi-key"),
                ) { Text(if (probeState.credentialConfigured) "Guardar nueva clave" else "Guardar clave") }
            },
            dismissButton = {
                TextButton(onClick = { showCredentialDialog = false; newCredential = "" }, modifier = Modifier.testTag("cancel-serpapi-key")) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun AccountAndBudgetCard(
    account: AccountSnapshot?,
    budget: LocalBudgetSnapshot?,
    limit: Int,
    onReduce: () -> Unit,
    expanded: Boolean,
    onExpand: () -> Unit,
) {
    val used = budget?.usedInWindow ?: 0
    val available = budget?.remainingInWindow ?: limit
    val fraction = (used.toFloat() / limit.coerceAtLeast(1)).coerceIn(0f, 1f)
    AppCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = onExpand)) {
            Text("Disponibles en SerpApi", style = MaterialTheme.typography.bodyLarge)
            Text(account?.totalSearchesLeft?.toString() ?: "No comprobado", style = MaterialTheme.typography.displaySmall)
            Text(account?.let { "Saldo consultado ${formatLocalTime(it.checkedAt.toEpochMilli())}" } ?: "Saldo pendiente de comprobar", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
        }
        if (expanded && account != null) {
            HorizontalDivider(color = FlightColors.Outline)
            Text("Estado: ${account.accountStatus}")
            Text("Saldo del plan: ${account.planSearchesLeft ?: "No informado"}")
            Text("Uso mensual: ${account.thisMonthUsage ?: "?"} / ${account.searchesPerMonth ?: "?"}")
            Text("Límite horario: ${account.thisHourSearches ?: "?"} / ${account.hourlyLimit ?: "?"}")
            Text("Renovación: ${account.planRenewalDate ?: "No informada"}")
        }
        HorizontalDivider(color = FlightColors.Outline)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Presupuesto local", style = MaterialTheme.typography.titleMedium)
            Text("$used de $limit", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("$available disponibles", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
            TextButton(onClick = onReduce) { Text("Reducir límite") }
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit,
    positive: Boolean = false,
    testTag: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp).then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VectorGlyph(icon, tint = FlightColors.OnSurface, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (positive) FlightColors.Success else FlightColors.SecondaryText)
        }
        VectorGlyph(R.drawable.ic_chevron_right, tint = FlightColors.SecondaryText)
    }
    HorizontalDivider(color = FlightColors.Outline)
}
