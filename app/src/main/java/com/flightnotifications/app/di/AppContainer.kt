package com.flightnotifications.app.di

import android.content.Context
import androidx.room.Room
import com.flightnotifications.app.data.local.ProbeDatabase
import com.flightnotifications.app.data.local.ProbePreferencesRepository
import com.flightnotifications.app.data.remote.RetrofitSerpApiClient
import com.flightnotifications.app.data.remote.SerpApiJsonParser
import com.flightnotifications.app.data.repository.LocalBudgetRepository
import com.flightnotifications.app.data.repository.SerpApiRepository
import com.flightnotifications.app.security.CredentialStore
import com.flightnotifications.app.security.KeystoreCredentialStore
import kotlinx.coroutines.sync.Mutex

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val database: ProbeDatabase = Room.databaseBuilder(
        applicationContext,
        ProbeDatabase::class.java,
        "flight_probe.db",
    ).build()

    val executionMutex = Mutex()
    val credentialStore: CredentialStore = KeystoreCredentialStore(applicationContext.noBackupFilesDir)
    val preferencesRepository = ProbePreferencesRepository(applicationContext)
    private val serpApiClient = RetrofitSerpApiClient()
    val serpApiRepository = SerpApiRepository(
        credentialStore = credentialStore,
        accountClient = serpApiClient,
        flightClient = serpApiClient,
        budgetRepository = LocalBudgetRepository(database.requestReservationDao()),
        responseParser = SerpApiJsonParser(),
        executionMutex = executionMutex,
    )
}
