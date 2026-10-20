package com.flightnotifications.app.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "flight_alerts", indices = [Index(value = ["enabled", "nextDueAt"])])
data class FlightAlertEntity(
    @PrimaryKey val id: String,
    val name: String,
    val originIata: String,
    val destinationIata: String,
    val outboundDate: String,
    val returnDate: String,
    val maxTotalCop: Long,
    val frequencyHours: Int,
    val directOnly: Boolean = true,
    val enabled: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAttemptAt: Long? = null,
    val lastAutomaticAttemptAt: Long? = null,
    val nextDueAt: Long? = null,
    @ColumnInfo(defaultValue = "1") val criteriaVersion: Long = 1,
    @ColumnInfo(defaultValue = "1") val notificationPolicyVersion: Long = 1,
    @ColumnInfo(defaultValue = "0") val candidateRotation: Int = 0,
)

@Entity(
    tableName = "flight_alert_results",
    foreignKeys = [ForeignKey(entity = FlightAlertEntity::class, parentColumns = ["id"], childColumns = ["alertId"], onDelete = ForeignKey.CASCADE)],
)
data class FlightAlertResultEntity(
    @PrimaryKey val alertId: String,
    val status: String,
    val lastAttemptAt: Long,
    val attemptCriteria: String? = null,
    val attemptMaximumCop: Long? = null,
    val networkReceivedAt: Long?,
    val lastError: String?,
    val lastVerifiedTotalCop: Long?,
    val lastVerifiedAt: Long?,
    val verifiedCriteria: String?,
    val selectedOfferJson: String?,
    val airlinesJson: String?,
    val candidatesConsidered: Int,
    val candidateLimit: Int,
    val explorationLimited: Boolean,
    val accountBalanceAtStart: Int?,
    val flightGetsStarted: Int,
    val googleFlightsUrl: String? = null,
    /** Bounded to the verified offers from the latest successful review; never a price history. */
    val verifiedOffersJson: String? = null,
)

@Entity(
    tableName = "sent_notifications",
    foreignKeys = [ForeignKey(entity = FlightAlertEntity::class, parentColumns = ["id"], childColumns = ["alertId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sentAt"]), Index(value = ["alertId"])],
)
data class SentNotificationEntity(
    @PrimaryKey val id: String,
    val alertId: String,
    val fingerprint: String,
    val itineraryIdentity: String,
    val priceCop: Long,
    val sentAt: Long,
    val routeSummary: String,
    val outboundCarrier: String,
    val returnCarrier: String,
)

/** Durable notification outbox used to recover a process death between notify() and Room commit. */
@Entity(
    tableName = "notification_outbox",
    foreignKeys = [ForeignKey(entity = FlightAlertEntity::class, parentColumns = ["id"], childColumns = ["alertId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["alertId"])],
)
data class NotificationOutboxEntity(
    /** Unique dispatch ID; stable only for retrying this event, not a fare deduplication key. */
    @PrimaryKey val fingerprint: String,
    val alertId: String,
    val itineraryIdentity: String,
    val priceCop: Long,
    val createdAt: Long,
    val routeSummary: String,
    val outboundCarrier: String,
    val returnCarrier: String,
    val explorationLimited: Boolean,
    val criteriaVersion: Long,
    val notificationPolicyVersion: Long,
)

@Entity(
    tableName = "notified_fingerprints",
    foreignKeys = [ForeignKey(entity = FlightAlertEntity::class, parentColumns = ["id"], childColumns = ["alertId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["alertId"]), Index(value = ["itineraryIdentity"])],
)
data class NotifiedFingerprintEntity(
    /** Unique dispatch ID used to make outbox commit/recovery idempotent. */
    @PrimaryKey val fingerprint: String,
    val alertId: String,
    val itineraryIdentity: String,
    val priceCop: Long,
    val firstSentAt: Long,
)

@Entity(
    tableName = "lowest_notified_prices",
    foreignKeys = [ForeignKey(entity = FlightAlertEntity::class, parentColumns = ["id"], childColumns = ["alertId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["alertId"])],
)
data class LowestNotifiedPriceEntity(
    @PrimaryKey val itineraryIdentity: String,
    val alertId: String,
    val lowestPriceCop: Long,
    val updatedAt: Long,
)

@Entity(tableName = "installation_metadata")
data class InstallationMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val exampleInitialized: Boolean,
)
