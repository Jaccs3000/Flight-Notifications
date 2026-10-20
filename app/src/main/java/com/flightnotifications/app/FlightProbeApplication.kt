package com.flightnotifications.app

import android.app.Application
import com.flightnotifications.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlightProbeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationDispatcher.createChannel()
        container.alertWorkScheduler.ensureScheduled()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.flightAlertRepository.initializeExample() }
        }
    }
}
