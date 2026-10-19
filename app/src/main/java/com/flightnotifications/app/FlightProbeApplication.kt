package com.flightnotifications.app

import android.app.Application
import com.flightnotifications.app.di.AppContainer

class FlightProbeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
