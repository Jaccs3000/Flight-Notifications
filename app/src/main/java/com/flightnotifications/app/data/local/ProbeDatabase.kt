package com.flightnotifications.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RequestReservationEntity::class,
        BudgetClockObservationEntity::class,
        FlightAlertEntity::class,
        FlightAlertResultEntity::class,
        SentNotificationEntity::class,
        NotificationOutboxEntity::class,
        NotifiedFingerprintEntity::class,
        LowestNotifiedPriceEntity::class,
        InstallationMetadataEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class ProbeDatabase : RoomDatabase() {
    abstract fun requestReservationDao(): RequestReservationDao
    abstract fun flightAlertDao(): FlightAlertDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `flight_alerts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `originIata` TEXT NOT NULL, `destinationIata` TEXT NOT NULL, `outboundDate` TEXT NOT NULL, `returnDate` TEXT NOT NULL, `maxTotalCop` INTEGER NOT NULL, `frequencyHours` INTEGER NOT NULL, `directOnly` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `lastAttemptAt` INTEGER, `lastAutomaticAttemptAt` INTEGER, `nextDueAt` INTEGER, `criteriaVersion` INTEGER NOT NULL DEFAULT 1, `notificationPolicyVersion` INTEGER NOT NULL DEFAULT 1, `candidateRotation` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flight_alerts_enabled_nextDueAt` ON `flight_alerts` (`enabled`, `nextDueAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `flight_alert_results` (`alertId` TEXT NOT NULL, `status` TEXT NOT NULL, `lastAttemptAt` INTEGER NOT NULL, `attemptCriteria` TEXT, `attemptMaximumCop` INTEGER, `networkReceivedAt` INTEGER, `lastError` TEXT, `lastVerifiedTotalCop` INTEGER, `lastVerifiedAt` INTEGER, `verifiedCriteria` TEXT, `selectedOfferJson` TEXT, `airlinesJson` TEXT, `candidatesConsidered` INTEGER NOT NULL, `candidateLimit` INTEGER NOT NULL, `explorationLimited` INTEGER NOT NULL, `accountBalanceAtStart` INTEGER, `flightGetsStarted` INTEGER NOT NULL, `googleFlightsUrl` TEXT, PRIMARY KEY(`alertId`), FOREIGN KEY(`alertId`) REFERENCES `flight_alerts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sent_notifications` (`id` TEXT NOT NULL, `alertId` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `itineraryIdentity` TEXT NOT NULL, `priceCop` INTEGER NOT NULL, `sentAt` INTEGER NOT NULL, `routeSummary` TEXT NOT NULL, `outboundCarrier` TEXT NOT NULL, `returnCarrier` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`alertId`) REFERENCES `flight_alerts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sent_notifications_sentAt` ON `sent_notifications` (`sentAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sent_notifications_alertId` ON `sent_notifications` (`alertId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `notified_fingerprints` (`fingerprint` TEXT NOT NULL, `alertId` TEXT NOT NULL, `itineraryIdentity` TEXT NOT NULL, `priceCop` INTEGER NOT NULL, `firstSentAt` INTEGER NOT NULL, PRIMARY KEY(`fingerprint`), FOREIGN KEY(`alertId`) REFERENCES `flight_alerts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notified_fingerprints_alertId` ON `notified_fingerprints` (`alertId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notified_fingerprints_itineraryIdentity` ON `notified_fingerprints` (`itineraryIdentity`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `lowest_notified_prices` (`itineraryIdentity` TEXT NOT NULL, `alertId` TEXT NOT NULL, `lowestPriceCop` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`itineraryIdentity`), FOREIGN KEY(`alertId`) REFERENCES `flight_alerts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lowest_notified_prices_alertId` ON `lowest_notified_prices` (`alertId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `installation_metadata` (`id` INTEGER NOT NULL, `exampleInitialized` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                // Existing PoC installations keep their data and never receive the new sample alert implicitly.
                db.execSQL("INSERT OR IGNORE INTO `installation_metadata` (`id`, `exampleInitialized`) VALUES (1, 1)")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `flight_alert_results` ADD COLUMN `verifiedOffersJson` TEXT")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notification_outbox` (`fingerprint` TEXT NOT NULL, `alertId` TEXT NOT NULL, `itineraryIdentity` TEXT NOT NULL, `priceCop` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `routeSummary` TEXT NOT NULL, `outboundCarrier` TEXT NOT NULL, `returnCarrier` TEXT NOT NULL, `explorationLimited` INTEGER NOT NULL, `criteriaVersion` INTEGER NOT NULL, `notificationPolicyVersion` INTEGER NOT NULL, PRIMARY KEY(`fingerprint`), FOREIGN KEY(`alertId`) REFERENCES `flight_alerts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_outbox_alertId` ON `notification_outbox` (`alertId`)")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `flight_alerts` ADD COLUMN `lastReviewCompletedAt` INTEGER")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sent_notifications` ADD COLUMN `outboundDepartureLocal` TEXT")
                db.execSQL("ALTER TABLE `sent_notifications` ADD COLUMN `returnDepartureLocal` TEXT")
                db.execSQL("ALTER TABLE `notification_outbox` ADD COLUMN `outboundDepartureLocal` TEXT")
                db.execSQL("ALTER TABLE `notification_outbox` ADD COLUMN `returnDepartureLocal` TEXT")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `flight_alerts` ADD COLUMN `adults18Plus` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `flight_alerts` ADD COLUMN `minorPassengersJson` TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
}
