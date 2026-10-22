package com.flightnotifications.app.di

import android.content.Context
import androidx.room.Room
import com.flightnotifications.app.data.local.ProbeDatabase
import com.flightnotifications.app.data.local.ProbePreferencesRepository
import com.flightnotifications.app.data.local.DiagnosticLog
import com.flightnotifications.app.data.local.FlightAlertDao
import com.flightnotifications.app.data.remote.RetrofitSerpApiClient
import com.flightnotifications.app.data.remote.SerpApiJsonParser
import com.flightnotifications.app.data.repository.LocalBudgetRepository
import com.flightnotifications.app.data.repository.SerpApiRepository
import com.flightnotifications.app.security.CredentialStore
import com.flightnotifications.app.security.KeystoreCredentialStore
import com.flightnotifications.app.data.notification.FlightNotificationDispatcher
import com.flightnotifications.app.data.worker.AlertWorkScheduler
import com.flightnotifications.app.domain.validation.ColombianAirportCatalog
import com.flightnotifications.app.domain.validation.ColombianAirlineCatalog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.first
import java.io.File

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val database: ProbeDatabase = Room.databaseBuilder(
        applicationContext,
        ProbeDatabase::class.java,
        "flight_probe.db",
    ).addMigrations(
        ProbeDatabase.MIGRATION_1_2,
        ProbeDatabase.MIGRATION_2_3,
        ProbeDatabase.MIGRATION_3_4,
        ProbeDatabase.MIGRATION_4_5,
    ).build()
    val flightAlertDao: FlightAlertDao = database.flightAlertDao()

    val executionMutex = Mutex()
    /** Serializes local alert edits without waiting for network requests guarded by executionMutex. */
    val alertMutationMutex = Mutex()
    val accountDiagnosticLog = DiagnosticLog(File(applicationContext.noBackupFilesDir, "serpapi-account-diagnostics.log"))
    val flightDiagnosticLog = DiagnosticLog(File(applicationContext.noBackupFilesDir, "serpapi-flight-diagnostics.log"))
    val credentialStore: CredentialStore = KeystoreCredentialStore(applicationContext.noBackupFilesDir)
    val preferencesRepository = ProbePreferencesRepository(applicationContext)
    val flightAlertRepository = com.flightnotifications.app.data.repository.FlightAlertRepository(flightAlertDao)
    val airportCatalog = ColombianAirportCatalog(applicationContext)
    val airlineCatalog = ColombianAirlineCatalog(applicationContext)
    val notificationDispatcher = FlightNotificationDispatcher(applicationContext, flightAlertRepository)
    val alertWorkScheduler = AlertWorkScheduler(applicationContext)
    private val serpApiClient = RetrofitSerpApiClient()
    val localBudgetRepository = LocalBudgetRepository(database.requestReservationDao(), maximumRequests = {
        preferencesRepository.preferences.first().localBudgetLimit
    })
    val serpApiRepository = SerpApiRepository(
        credentialStore = credentialStore,
        accountClient = serpApiClient,
        flightClient = serpApiClient,
        budgetRepository = localBudgetRepository,
        responseParser = SerpApiJsonParser(),
        executionMutex = executionMutex,
        accountDiagnosticLog = accountDiagnosticLog,
        flightDiagnosticLog = flightDiagnosticLog,
        domesticAirportCodes = airportCatalog.airports.map { it.iata }.toSet(),
    )
}
