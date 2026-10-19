package com.flightnotifications.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.flightnotifications.app.di.AppContainer

class ProbeViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(ProbeViewModel::class.java)) {
            throw IllegalArgumentException("ViewModel no soportado")
        }
        return ProbeViewModel(
            repository = container.serpApiRepository,
            credentialStore = container.credentialStore,
            preferencesRepository = container.preferencesRepository,
            executionMutex = container.executionMutex,
        ) as T
    }
}
