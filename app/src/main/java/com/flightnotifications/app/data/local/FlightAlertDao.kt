package com.flightnotifications.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FlightAlertDao {
    @Query("SELECT * FROM flight_alerts ORDER BY enabled DESC, createdAt DESC")
    abstract fun observeAlerts(): Flow<List<FlightAlertEntity>>

    @Query("SELECT * FROM flight_alerts ORDER BY enabled DESC, createdAt DESC")
    abstract suspend fun getAlerts(): List<FlightAlertEntity>

    @Query("SELECT * FROM flight_alert_results")
    abstract fun observeResults(): Flow<List<FlightAlertResultEntity>>

    @Query("SELECT * FROM flight_alert_results")
    abstract suspend fun getResults(): List<FlightAlertResultEntity>

    @Query("SELECT * FROM sent_notifications ORDER BY sentAt DESC LIMIT 100")
    abstract fun observeNotifications(): Flow<List<SentNotificationEntity>>

    @Query("SELECT * FROM flight_alerts WHERE id = :id LIMIT 1")
    abstract suspend fun getAlert(id: String): FlightAlertEntity?

    @Query("SELECT * FROM flight_alerts WHERE enabled = 1 ORDER BY COALESCE(nextDueAt, 0), COALESCE(lastAutomaticAttemptAt, 0)")
    abstract suspend fun getEnabledAlerts(): List<FlightAlertEntity>

    @Query("SELECT * FROM flight_alerts WHERE enabled = 1 AND (nextDueAt IS NULL OR nextDueAt <= :now) ORDER BY COALESCE(nextDueAt, 0), COALESCE(lastAutomaticAttemptAt, 0)")
    abstract suspend fun getDueAlerts(now: Long): List<FlightAlertEntity>

    @Upsert
    abstract suspend fun putAlert(alert: FlightAlertEntity)

    @Query("DELETE FROM flight_alerts WHERE id = :id")
    abstract suspend fun deleteAlert(id: String)

    @Query("UPDATE flight_alerts SET enabled = :enabled, updatedAt = :now, nextDueAt = :nextDueAt, notificationPolicyVersion = notificationPolicyVersion + 1 WHERE id = :id AND enabled != :enabled")
    abstract suspend fun setEnabled(id: String, enabled: Boolean, now: Long, nextDueAt: Long?)

    @Query("UPDATE flight_alerts SET nextDueAt = :nextDueAt WHERE id = :id AND enabled = 1")
    abstract suspend fun deferEnabledAlert(id: String, nextDueAt: Long): Int

    @Query("UPDATE flight_alerts SET lastAttemptAt = :attemptAt WHERE id = :id AND criteriaVersion = :criteriaVersion")
    abstract suspend fun recordManualAttemptIfCurrent(id: String, criteriaVersion: Long, attemptAt: Long): Int

    @Query("UPDATE flight_alerts SET lastAttemptAt = :attemptAt, lastAutomaticAttemptAt = :attemptAt, nextDueAt = :nextDueAt WHERE id = :id AND criteriaVersion = :criteriaVersion AND enabled = 1")
    abstract suspend fun recordAutomaticAttemptIfCurrent(id: String, criteriaVersion: Long, attemptAt: Long, nextDueAt: Long): Int

    @Query("UPDATE flight_alerts SET candidateRotation = candidateRotation + 1 WHERE id = :id AND criteriaVersion = :criteriaVersion")
    abstract suspend fun advanceCandidateRotation(id: String, criteriaVersion: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putResult(result: FlightAlertResultEntity)

    @Query("SELECT * FROM flight_alert_results WHERE alertId = :alertId LIMIT 1")
    abstract suspend fun getResult(alertId: String): FlightAlertResultEntity?

    @Query("UPDATE flight_alert_results SET status = :status, lastError = :status WHERE alertId = :alertId")
    abstract suspend fun markResultAsPrevious(alertId: String, status: String): Int

    @Query("SELECT * FROM notified_fingerprints WHERE fingerprint = :fingerprint LIMIT 1")
    abstract suspend fun getFingerprint(fingerprint: String): NotifiedFingerprintEntity?

    @Query("SELECT * FROM notification_outbox WHERE fingerprint = :fingerprint LIMIT 1")
    abstract suspend fun getOutboxItem(fingerprint: String): NotificationOutboxEntity?

    @Query("SELECT * FROM notification_outbox ORDER BY createdAt, fingerprint")
    abstract suspend fun getPendingNotifications(): List<NotificationOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertOutboxItem(value: NotificationOutboxEntity): Long

    @Query("DELETE FROM notification_outbox WHERE fingerprint = :fingerprint")
    protected abstract suspend fun deleteOutboxItem(fingerprint: String)

    @Query("SELECT * FROM lowest_notified_prices WHERE itineraryIdentity = :itineraryIdentity LIMIT 1")
    abstract suspend fun getLowestPrice(itineraryIdentity: String): LowestNotifiedPriceEntity?

    @Query("SELECT * FROM installation_metadata WHERE id = 1 LIMIT 1")
    protected abstract suspend fun metadata(): InstallationMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putMetadata(value: InstallationMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAlert(alert: FlightAlertEntity)

    @Transaction
    open suspend fun initializeExampleIfNewInstall(example: FlightAlertEntity) {
        if (metadata() != null) return
        insertAlert(example)
        putMetadata(InstallationMetadataEntity(exampleInitialized = true))
    }

    @Transaction
    open suspend fun commitNotification(notification: SentNotificationEntity, sent: NotifiedFingerprintEntity) {
        val previous = getFingerprint(sent.fingerprint)
        if (previous != null) return
        insertFingerprint(sent)
        val old = getLowestPrice(sent.itineraryIdentity)
        if (old == null || sent.priceCop < old.lowestPriceCop) {
            putLowestPrice(LowestNotifiedPriceEntity(sent.itineraryIdentity, sent.alertId, sent.priceCop, sent.firstSentAt))
        }
        insertNotification(notification)
        pruneNotifications()
    }

    /** Existing pending work may be retried; a committed fingerprint must never be enqueued again. */
    @Transaction
    open suspend fun enqueueNotification(value: NotificationOutboxEntity): Boolean {
        if (getFingerprint(value.fingerprint) != null) return false
        if (getOutboxItem(value.fingerprint) != null) return true
        return insertOutboxItem(value) != -1L
    }

    /** Commit the visible history and deduplication atomically, then remove its outbox record. */
    @Transaction
    open suspend fun completePendingNotification(fingerprint: String): Boolean {
        val pending = getOutboxItem(fingerprint) ?: return false
        if (getFingerprint(fingerprint) != null) {
            deleteOutboxItem(fingerprint)
            return false
        }
        val notification = SentNotificationEntity(
            id = pending.fingerprint,
            alertId = pending.alertId,
            fingerprint = pending.fingerprint,
            itineraryIdentity = pending.itineraryIdentity,
            priceCop = pending.priceCop,
            sentAt = pending.createdAt,
            routeSummary = pending.routeSummary,
            outboundCarrier = pending.outboundCarrier,
            returnCarrier = pending.returnCarrier,
        )
        val sent = NotifiedFingerprintEntity(
            fingerprint = pending.fingerprint,
            alertId = pending.alertId,
            itineraryIdentity = pending.itineraryIdentity,
            priceCop = pending.priceCop,
            firstSentAt = pending.createdAt,
        )
        insertFingerprint(sent)
        val old = getLowestPrice(sent.itineraryIdentity)
        if (old == null || sent.priceCop < old.lowestPriceCop) {
            putLowestPrice(LowestNotifiedPriceEntity(sent.itineraryIdentity, sent.alertId, sent.priceCop, sent.firstSentAt))
        }
        insertNotification(notification)
        deleteOutboxItem(fingerprint)
        pruneNotifications()
        return true
    }

    @Transaction
    open suspend fun discardPendingNotification(fingerprint: String) = deleteOutboxItem(fingerprint)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertFingerprint(value: NotifiedFingerprintEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putLowestPrice(value: LowestNotifiedPriceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertNotification(value: SentNotificationEntity)

    @Query("DELETE FROM sent_notifications WHERE id NOT IN (SELECT id FROM sent_notifications ORDER BY sentAt DESC LIMIT 100)")
    protected abstract suspend fun pruneNotifications()

    @Query("DELETE FROM sent_notifications")
    abstract suspend fun clearVisibleNotifications()

    @Query("DELETE FROM notified_fingerprints WHERE alertId = :alertId")
    protected abstract suspend fun deleteFingerprintsForAlert(alertId: String)

    @Query("DELETE FROM lowest_notified_prices WHERE alertId = :alertId")
    protected abstract suspend fun deleteLowestPricesForAlert(alertId: String)

    @Transaction
    open suspend fun clearExpiredNotificationState(alertId: String) {
        deleteFingerprintsForAlert(alertId)
        deleteLowestPricesForAlert(alertId)
    }
}
