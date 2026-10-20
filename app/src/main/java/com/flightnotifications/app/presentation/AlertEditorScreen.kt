package com.flightnotifications.app.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.domain.validation.ColombianAirportCatalog
import com.flightnotifications.app.presentation.AlertInput
import com.flightnotifications.app.ui.theme.FlightColors
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun AlertEditorScreen(
    alert: FlightAlertEntity?,
    error: String?,
    airportCatalog: ColombianAirportCatalog,
    onBack: () -> Unit,
    onFormChanged: () -> Unit,
    onSave: (AlertInput) -> Unit,
) {
    val today = LocalDate.now(ZoneId.of("America/Bogota"))
    var name by rememberSaveable(alert?.id) { mutableStateOf(alert?.name ?: "Cartagena ↔ Cali") }
    var origin by rememberSaveable(alert?.id) { mutableStateOf(alert?.originIata ?: "CTG") }
    var destination by rememberSaveable(alert?.id) { mutableStateOf(alert?.destinationIata ?: "CLO") }
    var outbound by rememberSaveable(alert?.id) { mutableStateOf(alert?.outboundDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today.plusDays(30)) }
    var returning by rememberSaveable(alert?.id) { mutableStateOf(alert?.returnDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today.plusDays(33)) }
    var maximum by rememberSaveable(alert?.id) { mutableStateOf(alert?.maxTotalCop?.toString() ?: "850000") }
    var frequency by rememberSaveable(alert?.id) { mutableIntStateOf(alert?.frequencyHours ?: 24) }
    var directOnly by rememberSaveable(alert?.id) { mutableStateOf(alert?.directOnly ?: true) }
    val highEstimate = (720L / frequency) * 4L
    val lowEstimate = (720L / frequency) * 2L
    val airportError = when {
        origin.isNotBlank() && destination.isNotBlank() && origin.equals(destination, ignoreCase = true) -> "El origen y el destino deben ser distintos."
        origin.isNotBlank() && !airportCatalog.containsDomesticAirport(origin) -> "Selecciona un aeropuerto colombiano del catálogo."
        destination.isNotBlank() && !airportCatalog.containsDomesticAirport(destination) -> "Selecciona un aeropuerto colombiano del catálogo."
        else -> null
    }
    val dateError = when {
        outbound.isBefore(today) -> "La fecha de ida no puede estar en el pasado."
        !returning.isAfter(outbound) -> "La fecha de regreso debe ser posterior a la ida."
        else -> null
    }
    val maximumError = maximum.toLongOrNull()?.let { if (it <= 0L) "El máximo total debe ser mayor que cero." else null }

    AppPage(modifier = Modifier.imePadding()) {
        StandardBack(if (alert == null) "Nueva alerta" else "Editar alerta", onBack)

        AppCard {
            Text("Ruta y fechas", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80); onFormChanged() },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("alert-name"),
            )
            if (name.isNotBlank() && name.trim().length !in 3..80) Text("El nombre debe tener entre 3 y 80 caracteres.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            AirportPicker("Origen", origin, airportCatalog) { origin = it.iata; onFormChanged() }
            AirportPicker("Destino", destination, airportCatalog) { destination = it.iata; onFormChanged() }
            airportError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DatePickerButton("Ida", outbound, today, Modifier.weight(1f)) { selected ->
                    outbound = selected
                    if (!returning.isAfter(selected)) returning = selected.plusDays(1)
                    onFormChanged()
                }
                DatePickerButton("Regreso", returning, outbound.plusDays(1), Modifier.weight(1f)) { returning = it; onFormChanged() }
            }
            dateError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        AppCard {
            Text("Precio y frecuencia", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = maximum,
                onValueChange = { maximum = it.filter(Char::isDigit).take(12); onFormChanged() },
                label = { Text("Máximo total") },
                suffix = { Text("COP", color = FlightColors.SecondaryText) },
                singleLine = true,
                visualTransformation = CopGroupingTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("alert-maximum"),
            )
            maximumError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text("Ida y vuelta · 1 adulto · Económica", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text("Solo vuelos directos", style = MaterialTheme.typography.titleMedium)
                    Text("Desactívalo para permitir escalas.", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
                }
                AppSwitch(checked = directOnly, onCheckedChange = { directOnly = it; onFormChanged() }, modifier = Modifier.testTag("alert-direct-only"))
            }

            Text("Revisar cada", style = MaterialTheme.typography.titleMedium, color = FlightColors.SecondaryText)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(6, 8, 12, 24).forEach { hours ->
                    if (frequency == hours) {
                        Button(onClick = { frequency = hours; onFormChanged() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("$hours h") }
                    } else {
                        TextButton(onClick = { frequency = hours; onFormChanged() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("$hours h") }
                    }
                }
            }
            Text("Estimación: $lowEstimate–$highEstimate consultas al mes.", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
            Text("Es una proyección: cada revisión puede usar entre 2 y 4 consultas de vuelos.", style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
            Text("Las aerolíneas no se filtran y la app no compra ni reserva tiquetes.", style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("alert-form-error"))
        }

        Button(
            onClick = {
                onSave(AlertInput(alert?.id, name, origin, destination, outbound, returning, maximum.toLongOrNull() ?: 0L, frequency, directOnly))
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("save-alert"),
        ) {
            Text(if (alert == null) "Guardar alerta" else "Guardar cambios", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            if (alert == null) "Se guardará pausada. Actívala cuando quieras." else "Se conservará el estado actual de la alerta.",
            style = MaterialTheme.typography.bodyMedium,
            color = FlightColors.SecondaryText,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private object CopGroupingTransformation : VisualTransformation {
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO"))

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit)
        val formatted = digits.toLongOrNull()?.let(numberFormat::format).orEmpty()
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                var seen = 0
                formatted.forEachIndexed { index, char ->
                    if (char.isDigit()) seen++
                    if (seen >= offset) return index + 1
                }
                return formatted.length
            }

            override fun transformedToOriginal(offset: Int): Int = formatted.take(offset.coerceIn(0, formatted.length)).count(Char::isDigit).coerceAtMost(digits.length)
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
