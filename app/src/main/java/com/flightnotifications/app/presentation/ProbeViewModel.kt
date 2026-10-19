package com.flightnotifications.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightnotifications.app.data.local.ProbePreferences
import com.flightnotifications.app.data.local.ProbePreferencesRepository
import com.flightnotifications.app.data.repository.FailureCode
import com.flightnotifications.app.data.repository.ProbeFailure
import com.flightnotifications.app.data.repository.SerpApiRepository
import com.flightnotifications.app.security.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class ProbeViewModel(
    private val repository: SerpApiRepository,
    private val credentialStore: CredentialStore,
    private val preferencesRepository: ProbePreferencesRepository,
    private val executionMutex: Mutex,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProbeUiState())
    val state: StateFlow<ProbeUiState> = mutableState.asStateFlow()
    private var activeProbe: Job? = null

    init {
        viewModelScope.launch {
            credentialStore.isConfigured().also { configured ->
                mutableState.value = mutableState.value.copy(credentialConfigured = configured)
            }
            repository.localBudget().also { budget -> mutableState.value = mutableState.value.copy(budget = budget) }
        }
        viewModelScope.launch {
            preferencesRepository.preferences
                .catch { /* Settings failure must not trigger network activity. */ }
                .collect { preferences ->
                    mutableState.value = mutableState.value.copy(
                        outboundDate = preferences.outboundDate,
                        returnDate = preferences.returnDate,
                        directOnly = preferences.directOnly,
                    )
                }
        }
    }

    fun saveCredential(apiKey: String) {
        if (apiKey.isBlank() || state.value.isRunningProbe || state.value.isCheckingAccount) return
        viewModelScope.launch {
            executionMutex.withLock {
                runCatching { credentialStore.save(apiKey.trim()) }
                    .onSuccess {
                        mutableState.value = mutableState.value.copy(
                            credentialConfigured = true,
                            account = null,
                            accountMessage = "Clave guardada de forma cifrada en este dispositivo.",
                            outcome = null,
                            errorMessage = null,
                        )
                    }
                    .onFailure {
                        mutableState.value = mutableState.value.copy(errorMessage = "No fue posible guardar la clave de forma segura.")
                    }
            }
        }
    }

    fun deleteCredential() {
        if (state.value.isRunningProbe || state.value.isCheckingAccount) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isDeletingCredential = true, errorMessage = null)
            executionMutex.withLock {
                runCatching { credentialStore.delete() }
                    .onSuccess {
                        mutableState.value = mutableState.value.copy(
                            credentialConfigured = false,
                            account = null,
                            accountMessage = null,
                            outcome = null,
                        )
                    }
                    .onFailure {
                        mutableState.value = mutableState.value.copy(errorMessage = "No fue posible eliminar la clave local.")
                    }
            }
            mutableState.value = mutableState.value.copy(isDeletingCredential = false)
        }
    }

    fun setOutboundDate(date: LocalDate) = updatePreferences { copy(outboundDate = date) }
    fun setReturnDate(date: LocalDate) = updatePreferences { copy(returnDate = date) }
    fun setDirectOnly(value: Boolean) = updatePreferences { copy(directOnly = value) }

    private fun updatePreferences(update: ProbePreferences.() -> ProbePreferences) {
        val current = state.value
        val preferences = ProbePreferences(current.outboundDate, current.returnDate, current.directOnly).update()
        mutableState.value = current.copy(
            outboundDate = preferences.outboundDate,
            returnDate = preferences.returnDate,
            directOnly = preferences.directOnly,
            outcome = null,
            errorMessage = null,
        )
        viewModelScope.launch { runCatching { preferencesRepository.update(preferences) } }
    }

    fun checkAccount() {
        if (!state.value.credentialConfigured || state.value.isCheckingAccount || state.value.isRunningProbe) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isCheckingAccount = true, accountMessage = null, errorMessage = null)
            try {
                val account = repository.checkAccount()
                mutableState.value = mutableState.value.copy(
                    account = account,
                    accountMessage = if (account.accountStatus.equals("Active", ignoreCase = true)) {
                        "Cuenta comprobada. Account API no consume búsquedas de vuelos."
                    } else {
                        "La cuenta no está activa; se bloquearán las búsquedas de vuelos."
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ProbeFailure) {
                mutableState.value = mutableState.value.copy(account = null, errorMessage = messageFor(failure.code))
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(account = null, errorMessage = "No se pudo comprobar la cuenta.")
            } finally {
                mutableState.value = mutableState.value.copy(isCheckingAccount = false)
            }
        }
    }

    fun requestFlightProbeConfirmation() {
        val current = state.value
        if (!current.credentialConfigured || current.isRunningProbe || current.isCheckingAccount) return
        mutableState.value = current.copy(showFlightConfirmation = true, errorMessage = null)
    }

    fun dismissFlightProbeConfirmation() {
        mutableState.value = mutableState.value.copy(showFlightConfirmation = false)
    }

    fun confirmFlightProbe() {
        val current = state.value
        if (!current.showFlightConfirmation || current.isRunningProbe) return
        val criteria = com.flightnotifications.app.domain.model.SearchCriteria(
            outboundDate = current.outboundDate,
            returnDate = current.returnDate,
            directOnly = current.directOnly,
        )
        criteria.validationError()?.let { error ->
            mutableState.value = current.copy(showFlightConfirmation = false, errorMessage = error)
            return
        }
        mutableState.value = current.copy(showFlightConfirmation = false, isRunningProbe = true, outcome = null, errorMessage = null)
        activeProbe = viewModelScope.launch {
            try {
                val outcome = repository.runRoundTrip(criteria)
                mutableState.value = mutableState.value.copy(outcome = outcome, account = outcome.accountAtStart)
            } catch (cancelled: CancellationException) {
                mutableState.value = mutableState.value.copy(errorMessage = "La prueba fue cancelada. Las llamadas ya reservadas permanecen contabilizadas.")
            } catch (failure: ProbeFailure) {
                mutableState.value = mutableState.value.copy(errorMessage = messageFor(failure.code))
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(errorMessage = "La prueba terminó con un error controlado.")
            } finally {
                val budget = runCatching { repository.localBudget() }.getOrNull()
                mutableState.value = mutableState.value.copy(isRunningProbe = false, budget = budget ?: mutableState.value.budget)
                activeProbe = null
            }
        }
    }

    fun cancelFlightProbe() {
        activeProbe?.cancel(CancellationException("User requested cancellation"))
    }

    private fun messageFor(code: FailureCode): String = when (code) {
        FailureCode.NO_CREDENTIAL -> "Guarda primero la clave de SerpApi."
        FailureCode.CREDENTIAL_UNREADABLE -> "La clave cifrada no se puede leer. Elimínala e introdúcela de nuevo."
        FailureCode.NETWORK -> "No fue posible conectar. Revisa la conexión e inténtalo manualmente otra vez."
        FailureCode.INVALID_CREDENTIAL -> "SerpApi rechazó la clave. Revísala e introdúcela de nuevo."
        FailureCode.RATE_LIMITED -> "SerpApi aplicó un límite temporal de solicitudes. No se reintentó automáticamente."
        FailureCode.SERVICE_UNAVAILABLE -> "SerpApi no está disponible en este momento. No se reintentó automáticamente."
        FailureCode.SERVICE_ERROR -> "SerpApi devolvió un error de solicitud. No se mostraron detalles sensibles."
        FailureCode.ACCOUNT_INVALID -> "La respuesta de cuenta está incompleta o tiene valores inconsistentes; se bloqueó la búsqueda."
        FailureCode.ACCOUNT_INACTIVE -> "La cuenta de SerpApi no está activa."
        FailureCode.QUOTA_INSUFFICIENT -> "La cuenta no tiene capacidad conservadora para dos GET de vuelos."
        FailureCode.LOCAL_QUOTA -> "El límite local de 200 GET en 30 días no permite iniciar esta prueba."
        FailureCode.CLOCK_UNTRUSTED -> "El reloj del dispositivo cambió de forma inesperada. La cuota de vuelos está bloqueada hasta que se revise el reloj."
        FailureCode.INVALID_CRITERIA -> "Revisa las fechas: ida desde hoy y regreso posterior a la ida."
        FailureCode.SEARCH_NOT_READY -> "SerpApi no indicó que la búsqueda estuviera terminada. No se inició polling."
        FailureCode.INVALID_RESPONSE -> "La respuesta no tiene el formato esperado; no se inferirá un resultado."
    }
}
