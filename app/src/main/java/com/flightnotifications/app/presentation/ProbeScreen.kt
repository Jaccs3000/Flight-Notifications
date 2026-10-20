package com.flightnotifications.app.presentation

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Composable
fun ProbeScreen(viewModel: ProbeViewModel, alertsViewModel: FlightAlertsViewModel? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            alertsViewModel?.let {
                FlightAlertsSection(it, state.localBudgetLimit)
                HorizontalDivider()
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Configuración y diagnóstico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("La prueba manual de vuelos es opcional y puede consumir hasta 2 GET. Las alertas automáticas se administran arriba.", style = MaterialTheme.typography.bodyMedium)
            }

            CredentialCard(
                configured = state.credentialConfigured,
                busy = state.isRunningProbe || state.isCheckingAccount || state.isDeletingCredential,
                onSave = viewModel::saveCredential,
                onDelete = { pendingDelete = true },
                onCheckAccount = viewModel::checkAccount,
            )

            DiagnosticLogCard(
                title = "Registro de conexión",
                emptyMessage = "Aún no hay eventos. Pulsa Probar conexión para registrar el intento.",
                logText = state.accountDiagnosticLog,
                onClear = viewModel::clearDiagnosticLog,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Registro de conexión SerpApi", state.accountDiagnosticLog))
                    Toast.makeText(context, "Registro copiado", Toast.LENGTH_SHORT).show()
                },
            )

            state.account?.let { AccountCard(it, state.accountMessage) }
            state.accountMessage?.takeIf { state.account == null }?.let { InfoCard(it) }

            LocalBudgetCard(
                maximum = state.localBudgetLimit,
                used = state.budget?.usedInWindow ?: 0,
                remaining = state.budget?.remainingInWindow ?: state.localBudgetLimit,
                onSave = viewModel::reduceLocalBudgetLimit,
            )

            SearchCard(
                state = state,
                onOutboundDate = { date -> viewModel.setOutboundDate(date) },
                onReturnDate = { date -> viewModel.setReturnDate(date) },
                onDirectOnly = viewModel::setDirectOnly,
                onStart = viewModel::requestFlightProbeConfirmation,
                onCancel = viewModel::cancelFlightProbe,
            )

            DiagnosticLogCard(
                title = "Registro de búsqueda de vuelos",
                emptyMessage = "Aún no hay eventos. Confirma una búsqueda de vuelos para registrar el intento.",
                logText = state.flightDiagnosticLog,
                onClear = viewModel::clearFlightDiagnosticLog,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Registro de búsqueda de vuelos SerpApi", state.flightDiagnosticLog))
                    Toast.makeText(context, "Registro de vuelos copiado", Toast.LENGTH_SHORT).show()
                },
            )

            state.errorMessage?.let { ErrorCard(it) }
            state.outcome?.let { OutcomeCard(it) }

            Text(
                "La fuente puede no devolver todas las aerolíneas del mercado. La app informa únicamente los operadores recibidos en estas respuestas.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
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
            confirmButton = { TextButton(onClick = viewModel::confirmFlightProbe) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissFlightProbeConfirmation) { Text("Cancelar") } },
        )
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("Eliminar clave") },
            text = { Text("Se borrará la clave cifrada de este dispositivo y se invalidarán el saldo y los resultados en memoria.") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = false; viewModel.deleteCredential() }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = false }) { Text("Volver") } },
        )
    }
}

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
                OutlinedButton(onClick = onCopy, enabled = logText.isNotBlank()) { Text("Copiar") }
                OutlinedButton(onClick = onClear, enabled = logText.isNotBlank()) { Text("Limpiar") }
            }
        }
    }
}

@Composable
private fun CredentialCard(
    configured: Boolean,
    busy: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCheckAccount: () -> Unit,
) {
    var apiKeyInput by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Clave de SerpApi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Clave privada") },
                placeholder = { Text(if (configured) "Clave configurada; escribe otra para sustituirla" else "Ingresa la clave en este dispositivo") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                enabled = !busy,
            )
            Text(
                if (configured) "Hay una clave cifrada guardada; su contenido no se vuelve a mostrar."
                else "La clave no se guarda en el proyecto ni se envía a este chat.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(apiKeyInput); apiKeyInput = "" },
                    enabled = !busy && apiKeyInput.isNotBlank(),
                ) { Text(if (configured) "Sustituir" else "Guardar cifrada") }
                if (configured) {
                    OutlinedButton(onClick = onCheckAccount, enabled = !busy) { Text("Probar conexión") }
                }
            }
            if (configured) TextButton(onClick = onDelete, enabled = !busy) { Text("Eliminar clave") }
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
                    val picker = DatePickerDialog(context, { _, year, month, day -> onOutboundDate(LocalDate.of(year, month + 1, day)) }, current.year, current.monthValue - 1, current.dayOfMonth)
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
                Switch(checked = state.directOnly, onCheckedChange = onDirectOnly, enabled = !state.isRunningProbe)
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
                    modifier = Modifier.weight(1f),
                ) { Text("Probar vuelo de ida y vuelta") }
                if (state.isRunningProbe) OutlinedButton(onClick = onCancel) { Text("Cancelar") }
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
private fun LocalBudgetCard(maximum: Int, used: Int, remaining: Int, onSave: (Int) -> Unit) {
    var draft by remember(maximum) { mutableStateOf(maximum.toString()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Presupuesto local de SerpApi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("$used / $maximum GET iniciados en la ventana conservadora de 30 días · $remaining disponibles.")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.filter(Char::isDigit).take(3) },
                    label = { Text("Nuevo máximo") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(onClick = { draft.toIntOrNull()?.let(onSave) }, enabled = draft.toIntOrNull()?.let { it in 1..maximum } == true) { Text("Reducir") }
            }
            Text("El máximo solo se reduce desde 200. Las reservas ya iniciadas no se eliminan; si el nuevo tope queda por debajo del consumo actual, las búsquedas se bloquean hasta que haya capacidad.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DateButton(label: String, date: LocalDate, modifier: Modifier, onClick: (LocalDate) -> Unit) {
    OutlinedButton(onClick = { onClick(date) }, modifier = modifier) {
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
                }) { Text("Abrir Google Flights") }
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
