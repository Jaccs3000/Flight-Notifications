package com.flightnotifications.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlightAlertMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ProbeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationPreservesBudgetAndDoesNotSeedSampleOnExistingInstall() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL("INSERT INTO flight_request_reservations (runId, stage, createdAtEpochMillis, budgetClockMillis) VALUES ('existing-run', 'OUTBOUND', 1000, 1000)")
            execSQL("INSERT INTO budget_clock_observation (id, wallClockEpochMillis, elapsedRealtimeMillis, effectiveBudgetClockMillis) VALUES (1, 1000, 10, 1000)")
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            7,
            true,
            ProbeDatabase.MIGRATION_1_2,
            ProbeDatabase.MIGRATION_2_3,
            ProbeDatabase.MIGRATION_3_4,
            ProbeDatabase.MIGRATION_4_5,
            ProbeDatabase.MIGRATION_5_6,
            ProbeDatabase.MIGRATION_6_7,
        ).use { database ->
            database.query("SELECT COUNT(*) FROM flight_request_reservations").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM flight_alerts").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT exampleInitialized FROM installation_metadata WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            database.query("PRAGMA table_info(flight_alerts)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(1)
                assertTrue(columns.contains("criteriaVersion"))
                assertTrue(columns.contains("lastReviewCompletedAt"))
                assertTrue(columns.contains("adults18Plus"))
                assertTrue(columns.contains("minorPassengersJson"))
            }
            database.query("PRAGMA table_info(flight_alert_results)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(1)
                assertTrue(columns.contains("attemptCriteria"))
                assertTrue(columns.contains("attemptMaximumCop"))
                assertTrue(columns.contains("verifiedCriteria"))
                assertTrue(columns.contains("verifiedOffersJson"))
            }
            database.query("SELECT COUNT(*) FROM notification_outbox").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            listOf("sent_notifications", "notification_outbox").forEach { table ->
                database.query("PRAGMA table_info($table)").use { cursor ->
                    val columns = mutableSetOf<String>()
                    while (cursor.moveToNext()) columns += cursor.getString(1)
                    assertTrue(columns.contains("outboundDepartureLocal"))
                    assertTrue(columns.contains("returnDepartureLocal"))
                }
            }
        }
    }

    @Test
    fun migrationV6ToV7PreservesAlertsAndAddsAdultPassengerDefaults() {
        helper.createDatabase(DATABASE_V6_NAME, 6).apply {
            execSQL(
                "INSERT INTO flight_alerts (id, name, originIata, destinationIata, outboundDate, returnDate, maxTotalCop, frequencyHours, directOnly, enabled, createdAt, updatedAt) " +
                    "VALUES ('v6-alert', 'Cartagena a Cali', 'CTG', 'CLO', '2027-03-25', '2027-03-28', 400000, 24, 1, 1, 100, 100)",
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_V6_NAME, 7, true, ProbeDatabase.MIGRATION_6_7).use { database ->
            database.query("SELECT name, adults18Plus, minorPassengersJson FROM flight_alerts WHERE id = 'v6-alert'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Cartagena a Cali", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals("[]", cursor.getString(2))
            }
        }
    }

    @Test
    fun migrationV5ToV6AddsNullableDepartureTimesWithoutLosingSavedNotices() {
        helper.createDatabase(DATABASE_V5_NAME, 5).apply {
            execSQL(
                "INSERT INTO flight_alerts (id, name, originIata, destinationIata, outboundDate, returnDate, maxTotalCop, frequencyHours, directOnly, enabled, createdAt, updatedAt) " +
                    "VALUES ('v5-alert', 'Cartagena a Cali', 'CTG', 'CLO', '2027-03-25', '2027-03-28', 400000, 24, 1, 1, 100, 100)",
            )
            execSQL(
                "INSERT INTO sent_notifications (id, alertId, fingerprint, itineraryIdentity, priceCop, sentAt, routeSummary, outboundCarrier, returnCarrier) " +
                    "VALUES ('v5-notice', 'v5-alert', 'v5-notice', 'old-itinerary', 350000, 200, 'CTG ↔ CLO', 'JetSMART', 'JetSMART')",
            )
            execSQL(
                "INSERT INTO notification_outbox (fingerprint, alertId, itineraryIdentity, priceCop, createdAt, routeSummary, outboundCarrier, returnCarrier, explorationLimited, criteriaVersion, notificationPolicyVersion) " +
                    "VALUES ('v5-pending', 'v5-alert', 'pending-itinerary', 340000, 300, 'CTG ↔ CLO', 'Avianca', 'Avianca', 0, 1, 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_V5_NAME, 6, true, ProbeDatabase.MIGRATION_5_6).use { database ->
            database.query("SELECT routeSummary, outboundDepartureLocal, returnDepartureLocal FROM sent_notifications WHERE id = 'v5-notice'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("CTG ↔ CLO", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
            database.query("SELECT outboundCarrier, outboundDepartureLocal, returnDepartureLocal FROM notification_outbox WHERE fingerprint = 'v5-pending'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Avianca", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
        }
    }

    @Test
    fun migrationV2ToV5PreservesLastResultAndCreatesNotificationOutbox() {
        helper.createDatabase(DATABASE_V2_NAME, 2).apply {
            execSQL(
                "INSERT INTO flight_alerts (id, name, originIata, destinationIata, outboundDate, returnDate, maxTotalCop, frequencyHours, directOnly, enabled, createdAt, updatedAt) " +
                    "VALUES ('legacy-alert', 'Legado', 'CTG', 'CLO', '2027-03-25', '2027-03-28', 400000, 24, 1, 0, 100, 100)",
            )
            execSQL(
                "INSERT INTO flight_alert_results (alertId, status, lastAttemptAt, selectedOfferJson, candidatesConsidered, candidateLimit, explorationLimited, flightGetsStarted) " +
                    "VALUES ('legacy-alert', 'VERIFICADO', 200, '{\"offer\":\"legacy\"}', 1, 3, 0, 2)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_V2_NAME,
            5,
            true,
            ProbeDatabase.MIGRATION_2_3,
            ProbeDatabase.MIGRATION_3_4,
            ProbeDatabase.MIGRATION_4_5,
        ).use { database ->
            database.query("SELECT status, selectedOfferJson, verifiedOffersJson FROM flight_alert_results WHERE alertId = 'legacy-alert'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("VERIFICADO", cursor.getString(0))
                assertEquals("{\"offer\":\"legacy\"}", cursor.getString(1))
                assertTrue(cursor.isNull(2))
            }
            database.query("PRAGMA table_info(notification_outbox)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns += cursor.getString(1)
                assertTrue(columns.contains("fingerprint"))
                assertTrue(columns.contains("criteriaVersion"))
                assertTrue(columns.contains("notificationPolicyVersion"))
            }
        }
    }

    @Test
    fun migrationV4ToV5AddsReviewTimestampWithoutLosingExistingAlertData() {
        helper.createDatabase(DATABASE_V4_NAME, 4).apply {
            execSQL(
                "INSERT INTO flight_alerts (id, name, originIata, destinationIata, outboundDate, returnDate, maxTotalCop, frequencyHours, directOnly, enabled, createdAt, updatedAt, lastAttemptAt) " +
                    "VALUES ('legacy-v4-alert', 'Cartagena a Cali', 'CTG', 'CLO', '2027-03-25', '2027-03-28', 400000, 24, 1, 0, 100, 120, 110)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_V4_NAME,
            5,
            true,
            ProbeDatabase.MIGRATION_4_5,
        ).use { database ->
            database.query("SELECT name, maxTotalCop, lastAttemptAt, lastReviewCompletedAt FROM flight_alerts WHERE id = 'legacy-v4-alert'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Cartagena a Cali", cursor.getString(0))
                assertEquals(400000, cursor.getInt(1))
                assertEquals(110, cursor.getInt(2))
                assertTrue(cursor.isNull(3))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "flight_alert_migration_test.db"
        const val DATABASE_V2_NAME = "flight_alert_migration_v2_test.db"
        const val DATABASE_V4_NAME = "flight_alert_migration_v4_test.db"
        const val DATABASE_V5_NAME = "flight_alert_migration_v5_test.db"
        const val DATABASE_V6_NAME = "flight_alert_migration_v6_test.db"
    }
}
