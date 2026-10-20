package com.flightnotifications.app.presentation

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightnotifications.app.R
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.local.FlightAlertResultEntity
import com.flightnotifications.app.domain.model.VerifiedRoundTripOffer
import com.flightnotifications.app.domain.validation.ColombianAirport
import com.flightnotifications.app.domain.validation.ColombianAirportCatalog
import com.flightnotifications.app.ui.theme.FlightColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val ColombiaZone = ZoneId.of("America/Bogota")
private val SpanishLocale = Locale.forLanguageTag("es-CO")
private val OfferJson = Json { ignoreUnknownKeys = true }

@Composable
internal fun AppPage(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
internal fun AppToolbar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Volver", tint = FlightColors.OnSurface)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = FlightColors.Surface,
        border = BorderStroke(1.dp, FlightColors.Outline),
        shadowElevation = 0.5.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun SectionHeading(title: String, supporting: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText) }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun VectorGlyph(resourceId: Int, modifier: Modifier = Modifier, description: String? = null, tint: Color = FlightColors.SecondaryText) {
    Icon(painter = painterResource(resourceId), contentDescription = description, tint = tint, modifier = modifier)
}

@Composable
internal fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = FlightColors.Primary,
            checkedBorderColor = FlightColors.Primary,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFD7DEE2),
            uncheckedBorderColor = Color(0xFF9AA7AE),
            disabledCheckedThumbColor = Color.White,
            disabledCheckedTrackColor = FlightColors.Primary.copy(alpha = 0.45f),
            disabledUncheckedThumbColor = Color(0xFFF5F6F7),
            disabledUncheckedTrackColor = Color(0xFFE5E9EB),
        ),
    )
}

@Composable
internal fun StatusPill(text: String, positive: Boolean = false) {
    val background = if (positive) FlightColors.SuccessSurface else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (positive) FlightColors.Success else FlightColors.SecondaryText
    Surface(color = background, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (positive) VectorGlyph(R.drawable.ic_check_circle, modifier = Modifier.size(16.dp), tint = foreground)
            Text(text, color = foreground, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
internal fun AirportPicker(
    label: String,
    selectedCode: String,
    catalog: ColombianAirportCatalog,
    onSelect: (ColombianAirport) -> Unit,
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    val selected = catalog.find(selectedCode)
    Box {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(MaterialTheme.shapes.small).clickable { expanded = true },
            shape = MaterialTheme.shapes.small,
            color = FlightColors.Surface,
            border = BorderStroke(1.dp, FlightColors.Outline),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VectorGlyph(R.drawable.ic_flight, tint = FlightColors.SecondaryText)
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
                    Text(selected?.let { "${it.city} (${it.iata})" } ?: "Seleccionar aeropuerto", style = MaterialTheme.typography.bodyLarge)
                }
                VectorGlyph(R.drawable.ic_chevron_right, tint = FlightColors.SecondaryText)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                catalog.airports.sortedBy { it.city }.forEach { airport ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("${airport.city} (${airport.iata})", fontWeight = FontWeight.Medium)
                                Text(airport.name, style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
                            }
                        },
                        onClick = { onSelect(airport); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DatePickerButton(label: String, date: LocalDate, minimum: LocalDate, modifier: Modifier = Modifier, onSelect: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.heightIn(min = 68.dp).clip(MaterialTheme.shapes.small).clickable {
            val dialog = DatePickerDialog(context, { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day)) }, date.year, date.monthValue - 1, date.dayOfMonth)
            dialog.datePicker.minDate = minimum.atStartOfDay(ColombiaZone).toInstant().toEpochMilli()
            dialog.show()
        },
        shape = MaterialTheme.shapes.small,
        color = FlightColors.Surface,
        border = BorderStroke(1.dp, FlightColors.Outline),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VectorGlyph(R.drawable.ic_calendar)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = FlightColors.SecondaryText)
                Text(formatDate(date), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun EmptyState(title: String, detail: String, action: (@Composable () -> Unit)? = null) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
        action?.invoke()
    }
}

internal fun formatCop(amount: Long): String = NumberFormat.getCurrencyInstance(SpanishLocale).apply {
    currency = Currency.getInstance("COP")
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}.format(amount).replace('\u00a0', ' ')

internal fun formatDate(date: LocalDate): String = DateTimeFormatter.ofPattern("d MMM yyyy", SpanishLocale).format(date)
    .replace("ene.", "ene").replace("abr.", "abr").replace("ago.", "ago").replace("dic.", "dic")

internal fun formatDateRange(outbound: String, returning: String): String = runCatching {
    val start = LocalDate.parse(outbound)
    val end = LocalDate.parse(returning)
    val month = DateTimeFormatter.ofPattern("MMM", SpanishLocale).format(start).replace(".", "")
    val endMonth = DateTimeFormatter.ofPattern("MMM", SpanishLocale).format(end).replace(".", "")
    if (start.year == end.year && start.month == end.month) "${start.dayOfMonth}–${end.dayOfMonth} $month ${start.year}"
    else "${formatDate(start)} – ${formatDate(end)}"
}.getOrElse { "$outbound – $returning" }

internal fun formatLocalTime(epochMillis: Long): String = DateTimeFormatter.ofPattern("HH:mm", SpanishLocale)
    .withZone(ColombiaZone).format(Instant.ofEpochMilli(epochMillis))

internal fun formatLocalFull(epochMillis: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm 'COT'", SpanishLocale)
    .withZone(ColombiaZone).format(Instant.ofEpochMilli(epochMillis))

internal fun cityName(code: String, catalog: ColombianAirportCatalog): String = catalog.find(code)?.city ?: code

internal fun formatStops(stops: Int): String = if (stops <= 0) "directo" else "$stops escala(s)"

internal fun resultIsCurrent(alert: FlightAlertEntity, result: FlightAlertResultEntity): Boolean {
    val criteria = "${alert.originIata}-${alert.destinationIata}|${alert.outboundDate}|${alert.returnDate}|direct=${alert.directOnly}"
    return result.status == "VERIFICADO" && result.verifiedCriteria == criteria &&
        result.verifiedCriteria == result.attemptCriteria && result.attemptMaximumCop == alert.maxTotalCop
}

internal fun storedOffers(result: FlightAlertResultEntity?): List<VerifiedRoundTripOffer> = result?.verifiedOffersJson
    ?.let { runCatching { OfferJson.decodeFromString<List<VerifiedRoundTripOffer>>(it) }.getOrNull() }
    ?.takeIf { it.isNotEmpty() }
    ?: result?.selectedOfferJson?.let {
        runCatching { listOf(OfferJson.decodeFromString<VerifiedRoundTripOffer>(it)) }.getOrNull()
    }.orEmpty()

@Composable
internal fun StandardBack(title: String, onBack: () -> Unit, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    AppToolbar(title = title, onBack = onBack, subtitle = subtitle, trailing = trailing)
}

internal fun isPastOutbound(alert: FlightAlertEntity): Boolean = runCatching {
    LocalDate.now(ColombiaZone).isAfter(LocalDate.parse(alert.outboundDate))
}.getOrDefault(true)

@Composable
internal fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun SmallDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = FlightColors.Outline)
}

@Composable
internal fun SpacerHeight(height: Dp = 4.dp) {
    Spacer(Modifier.height(height))
}
