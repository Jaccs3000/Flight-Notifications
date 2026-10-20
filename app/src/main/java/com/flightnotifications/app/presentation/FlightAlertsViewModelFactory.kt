package com.flightnotifications.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.flightnotifications.app.di.AppContainer

class FlightAlertsViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(FlightAlertsViewModel::class.java)) {
            throw IllegalArgumentException("ViewModel no soportado")
        }
        return FlightAlertsViewModel(container) as T
    }
}
