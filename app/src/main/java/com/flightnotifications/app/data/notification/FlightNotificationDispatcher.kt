package com.flightnotifications.app.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.flightnotifications.app.MainActivity
import com.flightnotifications.app.R
import com.flightnotifications.app.data.local.NotificationOutboxEntity
import com.flightnotifications.app.domain.model.AlertSearchResult
import com.flightnotifications.app.data.repository.FlightAlertRepository
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.domain.model.VerifiedRoundTripOffer
import com.flightnotifications.app.domain.model.FlightDepartureTime
import com.flightnotifications.app.domain.model.MinorPassenger
import com.flightnotifications.app.domain.model.PassengerSelection
import kotlinx.serialization.json.Json
import com.flightnotifications.app.domain.validation.OfferNotificationPolicy
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.UUID

enum class DispatchStatus { SENT, NO_ELIGIBLE_OFFER, NOTIFICATION_PERMISSION_MISSING, ALERT_INACTIVE, STALE_CRITERIA, FAILED }

class FlightNotificationDispatcher(
    private val context: Context,
    private val outboxStore: NotificationOutboxStore,
    private val airportTimeZoneFor: (String) -> ZoneId? = { null },
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val deliveryCoordinator = NotificationDeliveryCoordinator(outboxStore, NotificationPublisher(::publish))

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Alertas de vuelos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ofertas verificadas que cumplen el máximo definido en una alerta."
            },
        )
    }

    fun notificationsAllowed(): Boolean {
        val runtimePermission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimePermission && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    suspend fun dispatchIfEligible(
        alert: FlightAlertEntity,
        outcome: AlertSearchResult,
        repository: FlightAlertRepository,
    ): DispatchStatus {
        val originZone = airportTimeZoneFor(alert.originIata) ?: COLOMBIA_ZONE
        val finalOutboundDate = if (alert.flexibleDateSearch) alert.outboundDateEnd.ifBlank { alert.outboundDate } else alert.outboundDate
        if (!alert.enabled || LocalDate.now(originZone).isAfter(LocalDate.parse(finalOutboundDate))) return DispatchStatus.ALERT_INACTIVE
        if (!notificationsAllowed()) return DispatchStatus.NOTIFICATION_PERMISSION_MISSING
        val eligible = mutableListOf<OfferNotificationPolicy.EligibleOffer<VerifiedRoundTripOffer>>()
        for (offer in outcome.offers) {
            val departure = offer.outbound.firstOrNull()?.departureLocal?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: continue
            val today = LocalDate.now(originZone)
            if (today.isAfter(departure.toLocalDate()) ||
                (today == departure.toLocalDate() && !departure.isAfter(LocalDateTime.now(originZone)))) continue
            if (OfferNotificationPolicy.isEligible(offer.totalCop, alert.maxTotalCop)) {
                eligible += OfferNotificationPolicy.EligibleOffer(offer, offer.totalCop)
            }
        }
        val selected = OfferNotificationPolicy.selectCheapestEligible(eligible)?.value
            ?: return DispatchStatus.NO_ELIGIBLE_OFFER
        val current = repository.find(alert.id) ?: return DispatchStatus.STALE_CRITERIA
        if (current.criteriaVersion != alert.criteriaVersion || current.notificationPolicyVersion != alert.notificationPolicyVersion ||
            current.originIata != alert.originIata || current.destinationIata != alert.destinationIata ||
            current.outboundDate != alert.outboundDate || current.returnDate != alert.returnDate || current.directOnly != alert.directOnly ||
            current.flexibleDateSearch != alert.flexibleDateSearch || current.outboundDateEnd != alert.outboundDateEnd || current.returnDateEnd != alert.returnDateEnd ||
            current.adults18Plus != alert.adults18Plus || current.minorPassengersJson != alert.minorPassengersJson ||
            current.maxTotalCop != alert.maxTotalCop || !current.enabled
        ) return DispatchStatus.STALE_CRITERIA

        val returnTrip = alert.returnDate.isNotBlank()
        val arrow = if (returnTrip) "↔" else "→"
        val dates = listOfNotNull(
            selected.outbound.firstOrNull()?.departureLocal?.take(10),
            selected.returning.firstOrNull()?.departureLocal?.take(10),
        ).joinToString(" / ").ifBlank { if (returnTrip) "${alert.outboundDate} / ${alert.returnDate}" else alert.outboundDate }
        val passengers = PassengerSelection.summary(
            alert.adults18Plus,
            runCatching { Json.decodeFromString<List<MinorPassenger>>(alert.minorPassengersJson) }.getOrDefault(emptyList()),
        )
        val route = "${alert.originIata} $arrow ${alert.destinationIata} · $dates · ${if (returnTrip) "ida y vuelta" else "solo ida"} · $passengers"
        // A unique delivery ID makes retries of this review idempotent without suppressing
        // the same fare in a later review.
        val deliveryId = UUID.randomUUID().toString()
        val pending = NotificationOutboxEntity(
            fingerprint = deliveryId,
            alertId = alert.id,
            itineraryIdentity = selected.itineraryIdentity,
            priceCop = selected.totalCop,
            createdAt = System.currentTimeMillis(),
            routeSummary = route,
            outboundCarrier = selected.outboundAirline,
            returnCarrier = selected.returnAirline,
            outboundDepartureLocal = selected.outbound.firstOrNull()?.departureLocal,
            returnDepartureLocal = selected.returning.firstOrNull()?.departureLocal,
            explorationLimited = outcome.explorationLimited,
            criteriaVersion = alert.criteriaVersion,
            notificationPolicyVersion = alert.notificationPolicyVersion,
        )
        return when (deliveryCoordinator.deliver(pending)) {
            NotificationDeliveryResult.SENT -> DispatchStatus.SENT
            NotificationDeliveryResult.PERMISSION_MISSING -> DispatchStatus.NOTIFICATION_PERMISSION_MISSING
            NotificationDeliveryResult.FAILED -> DispatchStatus.FAILED
            NotificationDeliveryResult.ALREADY_SENT, NotificationDeliveryResult.DISCARDED -> DispatchStatus.NO_ELIGIBLE_OFFER
        }
    }

    /** Replays durable pending notices before a new worker review consumes API quota. */
    suspend fun retryPending(repository: FlightAlertRepository): List<NotificationDeliveryResult> {
        if (!notificationsAllowed()) {
            return if (outboxStore.pending().isEmpty()) emptyList() else listOf(NotificationDeliveryResult.PERMISSION_MISSING)
        }
        return deliveryCoordinator.retryPending { pending ->
            val current = repository.find(pending.alertId)
            val originZone = current?.let { airportTimeZoneFor(it.originIata) } ?: COLOMBIA_ZONE
            current != null && current.enabled &&
                current.criteriaVersion == pending.criteriaVersion &&
                current.notificationPolicyVersion == pending.notificationPolicyVersion &&
                !LocalDate.now(originZone).isAfter(LocalDate.parse(if (current.flexibleDateSearch) current.outboundDateEnd.ifBlank { current.outboundDate } else current.outboundDate))
        }
    }

    private suspend fun publish(item: NotificationOutboxEntity) {
        val departureSummary = listOfNotNull(
            FlightDepartureTime.format(item.outboundDepartureLocal)?.let { "ida $it" },
            FlightDepartureTime.format(item.returnDepartureLocal)?.let { "regreso $it" },
        ).joinToString(" · ").takeIf(String::isNotEmpty)?.let { " · Salidas: $it" }.orEmpty()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flight)
            .setContentTitle("Tarifa más baja verificada")
            .setContentText("${item.routeSummary}$departureSummary · ${formatCop(item.priceCop)}")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "${item.routeSummary}$departureSummary · ${formatCop(item.priceCop)} ${if (item.returnCarrier.isBlank()) "solo ida" else "ida y vuelta"} · " +
                        "${item.outboundCarrier}${item.returnCarrier.takeIf(String::isNotBlank)?.let { " / $it" }.orEmpty()}. Precio observado en Google Flights; verifica condiciones antes de comprar. " +
                        "La misma tarifa puede avisarse otra vez en una próxima revisión." +
                        if (item.explorationLimited) " Exploración parcial: no se verificaron todas las combinaciones." else "",
                ),
            )
            .setContentIntent(alertPendingIntent(item.alertId, stableNotificationId(item.fingerprint)))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(stableNotificationId(item.fingerprint), notification)
    }

    fun cancelAllVisible() {
        manager.cancelAll()
    }

    private fun alertPendingIntent(alertId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_ALERT_ID, alertId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stableNotificationId(fingerprint: String): Int =
        fingerprint.takeLast(8).toLongOrNull(16)?.toInt()?.takeIf { it != 0 } ?: fingerprint.hashCode().let { if (it == 0) 1 else it }

    private fun formatCop(amount: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).apply {
        currency = Currency.getInstance("COP")
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }.format(amount)

    companion object {
        const val CHANNEL_ID = "verified_flight_deals"
        private val COLOMBIA_ZONE = ZoneId.of("America/Bogota")
    }
}
