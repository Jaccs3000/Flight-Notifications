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
            5,
            true,
            ProbeDatabase.MIGRATION_1_2,
            ProbeDatabase.MIGRATION_2_3,
            ProbeDatabase.MIGRATION_3_4,
            ProbeDatabase.MIGRATION_4_5,
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
                var foundCriteriaVersion = false
                var foundReviewCompletion = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == "criteriaVersion") foundCriteriaVersion = true
                    if (cursor.getString(1) == "lastReviewCompletedAt") foundReviewCompletion = true
                }
                assertTrue(foundCriteriaVersion)
                assertTrue(foundReviewCompletion)
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
            database.query("SELECT lastReviewCompletedAt FROM flight_alerts WHERE id = 'legacy-alert'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
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

    private companion object {
        const val DATABASE_NAME = "flight_alert_migration_test.db"
        const val DATABASE_V2_NAME = "flight_alert_migration_v2_test.db"
    }
}
