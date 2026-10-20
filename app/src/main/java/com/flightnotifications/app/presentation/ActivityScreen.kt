package com.flightnotifications.app.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flightnotifications.app.R
import com.flightnotifications.app.data.local.SentNotificationEntity
import com.flightnotifications.app.domain.validation.ColombianAirportCatalog
import com.flightnotifications.app.ui.theme.FlightColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun ActivityScreen(
    state: FlightAlertsUiState,
    airportCatalog: ColombianAirportCatalog,
    onOpenAlert: (String, String) -> Unit,
    onClear: () -> Unit,
) {
    val zone = ZoneId.of("America/Bogota")
    val today = LocalDate.now(zone)
    val groups = state.notifications.sortedByDescending { it.sentAt }.groupBy { Instant.ofEpochMilli(it.sentAt).atZone(zone).toLocalDate() }

    AppPage {
        SectionHeading(
            title = "Actividad",
            supporting = "Avisos enviados",
            trailing = {
                TextButton(onClick = onClear, enabled = state.notifications.isNotEmpty(), modifier = Modifier.testTag("clear-activity")) {
                    VectorGlyph(R.drawable.ic_delete, tint = FlightColors.SecondaryText)
                    Text("Limpiar", modifier = Modifier.padding(start = 5.dp))
                }
            },
        )
        Text("La tarifa más baja de cada revisión. Una tarifa puede avisarse otra vez en revisiones futuras.", style = MaterialTheme.typography.bodyLarge, color = FlightColors.SecondaryText)

        if (groups.isEmpty()) {
            EmptyState("Aún no hay avisos", "Cuando una revisión encuentre la tarifa elegible más baja, el aviso aparecerá aquí.")
        } else {
            groups.forEach { (date, notices) ->
                Text(
                    when (date) {
                        today -> "Hoy"
                        today.minusDays(1) -> "Ayer"
                        else -> formatDate(date)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                notices.forEach { notice ->
                    val alert = state.alerts.firstOrNull { it.alert.id == notice.alertId }?.alert
                    ActivityNoticeCard(notice, alert, airportCatalog) { onOpenAlert(notice.alertId, notice.id) }
                }
            }
        }
    }
}

@Composable
private fun ActivityNoticeCard(
    notice: SentNotificationEntity,
    alert: com.flightnotifications.app.data.local.FlightAlertEntity?,
    airportCatalog: ColombianAirportCatalog,
    onClick: () -> Unit,
) {
    val savedCriteria = parseSavedNotificationRoute(notice.routeSummary)
    AppCard(contentPadding = 18.dp, modifier = Modifier.clickable(onClick = onClick).testTag("activity-notice-${notice.id}")) {
        StatusPill("Tarifa verificada", positive = true)
        val route = savedCriteria?.let { "${cityName(it.first, airportCatalog)} ↔ ${cityName(it.second, airportCatalog)}" }
            ?: alert?.let { "${cityName(it.originIata, airportCatalog)} ↔ ${cityName(it.destinationIata, airportCatalog)}" }
            ?: notice.routeSummary
        Text(route, style = MaterialTheme.typography.headlineSmall)
        val savedDates = savedCriteria?.third?.split('/')?.map(String::trim)?.takeIf { it.size == 2 }
        val dateText = savedDates?.let { runCatching { formatDateRange(it[0], it[1]) }.getOrNull() }
            ?: alert?.let { formatDateRange(it.outboundDate, it.returnDate) }
        dateText?.let { Text("$it · 1 adulto", style = MaterialTheme.typography.bodyLarge, color = FlightColors.SecondaryText) }
        Text(formatCop(notice.priceCop), style = MaterialTheme.typography.displaySmall, color = FlightColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("COP · ida y vuelta", style = MaterialTheme.typography.bodyLarge, color = FlightColors.SecondaryText)
        SmallDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("${notice.outboundCarrier} / ${notice.returnCarrier}", style = MaterialTheme.typography.bodyLarge, color = FlightColors.SecondaryText)
                Text("Aviso de ${formatLocalTime(notice.sentAt)} COT", style = MaterialTheme.typography.bodyMedium, color = FlightColors.SecondaryText)
            }
            VectorGlyph(R.drawable.ic_chevron_right, tint = FlightColors.SecondaryText)
        }
    }
}

private fun parseSavedNotificationRoute(value: String): Triple<String, String, String>? {
    val routeAndDates = value.split('·', limit = 2)
    val route = routeAndDates.firstOrNull()?.trim() ?: return null
    val dates = routeAndDates.getOrNull(1)?.trim() ?: return null
    val match = Regex("^([A-Z]{3})\\s*↔\\s*([A-Z]{3})$").matchEntire(route) ?: return null
    return Triple(match.groupValues[1], match.groupValues[2], dates)
}
