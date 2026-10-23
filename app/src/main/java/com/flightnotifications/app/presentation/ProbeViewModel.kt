package com.flightnotifications.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightnotifications.app.data.local.DiagnosticLog
import com.flightnotifications.app.data.local.ProbePreferences
import com.flightnotifications.app.data.local.ProbePreferencesRepository
import com.flightnotifications.app.data.repository.FailureCode
import com.flightnotifications.app.data.repository.ProbeFailure
import com.flightnotifications.app.data.repository.SerpApiRepository
import com.flightnotifications.app.data.repository.SavedAccountCheck
import com.flightnotifications.app.domain.model.LocalBudgetSnapshot
import com.flightnotifications.app.security.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
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
    private val accountDiagnosticLog: DiagnosticLog,
    private val flightDiagnosticLog: DiagnosticLog,
    private val budgetSnapshots: StateFlow<LocalBudgetSnapshot?>,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProbeUiState())
    val state: StateFlow<ProbeUiState> = mutableState.asStateFlow()
    private var activeProbe: Job? = null

    init {
        viewModelScope.launch {
            accountDiagnosticLog.load()
            accountDiagnosticLog.record("app_started package=com.flightnotifications.app")
            accountDiagnosticLog.text.collect { text ->
                mutableState.value = mutableState.value.copy(accountDiagnosticLog = text)
            }
        }
        viewModelScope.launch {
            flightDiagnosticLog.load()
            flightDiagnosticLog.text.collect { text ->
                mutableState.value = mutableState.value.copy(flightDiagnosticLog = text)
            }
        }
        viewModelScope.launch {
            flightDiagnosticLog.storageError.collect { error ->
                mutableState.value = mutableState.value.copy(flightDiagnosticStorageError = error)
            }
        }
        viewModelScope.launch {
            budgetSnapshots.filterNotNull().collect { budget ->
                mutableState.value = mutableState.value.copy(budget = budget)
            }
        }
        viewModelScope.launch {
            val configured = runCatching { credentialStore.credentials() }.getOrElse {
                mutableState.value = mutableState.value.copy(errorMessage = "No se pudieron leer las claves cifradas.")
                emptyList()
            }
            mutableState.value = mutableState.value.copy(credentialConfigured = configured.isNotEmpty())
            if (configured.isNotEmpty()) checkAccount()
            repository.localBudget().also { budget -> mutableState.value = mutableState.value.copy(budget = budget) }
        }
        viewModelScope.launch {
            runCatching { preferencesRepository.applyRequestedComparisonDatesOnce() }
            preferencesRepository.preferences
                .catch { /* Settings failure must not trigger network activity. */ }
                .collect { preferences ->
                    mutableState.value = mutableState.value.copy(
                        outboundDate = preferences.outboundDate,
                        returnDate = preferences.returnDate,
                        directOnly = preferences.directOnly,
                        localBudgetLimit = preferences.localBudgetLimit,
                    )
                }
        }
    }

    fun saveCredential(apiKey: String) {
        if (apiKey.isBlank() || state.value.isRunningProbe || state.value.isCheckingAccount) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                isCheckingAccount = true,
                errorMessage = null,
                credentialSaveCompleted = false,
                credentialSaveError = null,
                accountMessage = null,
            )
            try {
                val normalized = apiKey.trim()
                val duplicate = runCatching { credentialStore.credentials().any { it.apiKey == normalized } }.getOrDefault(false)
                if (duplicate) {
                    mutableState.value = mutableState.value.copy(credentialSaveError = "Esta API key ya está agregada.")
                    return@launch
                }
                val account = repository.checkAccount(normalized)
                credentialStore.add(normalized)
                check(credentialStore.credentials().any { it.apiKey == normalized }) { "CREDENTIAL_SAVE_NOT_CONFIRMED" }
                recordAccountDiagnostic("credential_added account_status=${account.accountStatus}")
                mutableState.value = mutableState.value.copy(
                    credentialConfigured = true,
                    credentialSaveCompleted = true,
                    accountMessage = "API key validada y guardada de forma cifrada.",
                    outcome = null,
                    errorMessage = null,
                )
                runCatching { refreshSavedAccounts() }
                    .onSuccess {
                        mutableState.value = mutableState.value.copy(accountMessage = "API key validada y guardada de forma cifrada.")
                    }
                    .onFailure {
                        mutableState.value = mutableState.value.copy(accountMessage = "API key guardada. No se pudo actualizar el saldo; toca Actualizar.")
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ProbeFailure) {
                recordAccountDiagnostic("credential_validation_failed category=${failure.code.name}")
                val message = messageFor(failure.code)
                mutableState.value = mutableState.value.copy(errorMessage = message, credentialSaveError = message)
            } catch (_: Exception) {
                recordAccountDiagnostic("credential_add_failed")
                val message = "No fue posible validar o guardar la API key. Revisa la conexión y vuelve a intentarlo."
                mutableState.value = mutableState.value.copy(errorMessage = message, credentialSaveError = message)
            } finally {
                mutableState.value = mutableState.value.copy(isCheckingAccount = false)
            }
        }
    }

    fun deleteCredential(id: String) {
        if (state.value.isRunningProbe || state.value.isCheckingAccount) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isDeletingCredential = true, errorMessage = null)
            executionMutex.withLock {
                runCatching { credentialStore.remove(id) }
                    .onSuccess {
                        recordAccountDiagnostic("credential_removed")
                        val remaining = runCatching { credentialStore.credentials() }.getOrDefault(emptyList())
                        mutableState.value = mutableState.value.copy(credentialConfigured = remaining.isNotEmpty(), outcome = null)
                    }
                    .onFailure {
                        recordAccountDiagnostic("credential_delete_failed")
                        mutableState.value = mutableState.value.copy(errorMessage = "No fue posible eliminar la clave local.")
                    }
            }
            mutableState.value = mutableState.value.copy(isDeletingCredential = false)
            if (state.value.credentialConfigured) checkAccount()
            else mutableState.value = mutableState.value.copy(credentialAccounts = emptyList(), account = null, accountMessage = null)
        }
    }

    fun setOutboundDate(date: LocalDate) = updatePreferences { copy(outboundDate = date) }
    fun setReturnDate(date: LocalDate) = updatePreferences { copy(returnDate = date) }
    fun setDirectOnly(value: Boolean) = updatePreferences { copy(directOnly = value) }

    fun reduceLocalBudgetLimit(value: Int) {
        val currentLimit = state.value.localBudgetLimit
        if (value !in 1..currentLimit) {
            mutableState.value = mutableState.value.copy(errorMessage = "El presupuesto local solo se puede reducir, entre 1 y $currentLimit GET.")
            return
        }
        updatePreferences { copy(localBudgetLimit = value) }
        mutableState.value = mutableState.value.copy(errorMessage = null)
    }

    private fun updatePreferences(update: ProbePreferences.() -> ProbePreferences) {
        val current = state.value
        val preferences = ProbePreferences(current.outboundDate, current.returnDate, current.directOnly, current.localBudgetLimit).update()
        mutableState.value = current.copy(
            outboundDate = preferences.outboundDate,
            returnDate = preferences.returnDate,
            directOnly = preferences.directOnly,
            localBudgetLimit = preferences.localBudgetLimit,
            outcome = null,
            errorMessage = null,
        )
        viewModelScope.launch {
            runCatching { preferencesRepository.update(preferences) }
                .onSuccess {
                    runCatching { repository.localBudget() }.onSuccess { budget ->
                        mutableState.value = mutableState.value.copy(budget = budget)
                    }
                }
                .onFailure { mutableState.value = mutableState.value.copy(errorMessage = "No se pudieron guardar los ajustes locales.") }
        }
    }

    fun checkAccount() {
        if (!state.value.credentialConfigured || state.value.isCheckingAccount || state.value.isRunningProbe) return
        viewModelScope.launch {
            recordAccountDiagnostic("ui_action account_check")
            mutableState.value = mutableState.value.copy(isCheckingAccount = true, accountMessage = null, errorMessage = null)
            try {
                refreshSavedAccounts()
                recordAccountDiagnostic("account_check_completed accounts=${state.value.credentialAccounts.size}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ProbeFailure) {
                recordAccountDiagnostic("account_check_failed category=${failure.code.name}")
                mutableState.value = mutableState.value.copy(account = null, credentialAccounts = emptyList(), errorMessage = messageFor(failure.code))
            } catch (_: Exception) {
                recordAccountDiagnostic("account_check_failed category=UNEXPECTED")
                mutableState.value = mutableState.value.copy(account = null, credentialAccounts = emptyList(), errorMessage = "No se pudo comprobar la cuenta.")
            } finally {
                mutableState.value = mutableState.value.copy(isCheckingAccount = false)
            }
        }
    }

    private suspend fun refreshSavedAccounts() {
        val checks = repository.checkSavedAccounts()
        val accountRows = checks.map(::toCredentialUi)
        val selected = checks.firstOrNull { check ->
            check.account?.accountStatus.equals("Active", ignoreCase = true) && (check.account?.conservativeSearchesLeft ?: 0) > 0
        }?.account ?: checks.firstNotNullOfOrNull { it.account }
        val availableAccounts = accountRows.count { it.active }
        val summary = when {
            checks.isEmpty() -> null
            availableAccounts > 0 -> "$availableAccounts cuenta(s) disponibles. Se probarán en el orden configurado, saltando las que no tengan consultas."
            checks.any { it.account?.accountStatus.equals("Active", ignoreCase = true) } -> "Las cuentas están activas, pero no tienen saldo dentro del límite disponible."
            else -> "No hay una cuenta activa disponible; revisa las claves guardadas."
        }
        mutableState.value = mutableState.value.copy(
            credentialConfigured = checks.isNotEmpty(),
            credentialAccounts = accountRows,
            account = selected,
            accountMessage = summary ?: mutableState.value.accountMessage,
        )
    }

    private fun toCredentialUi(check: SavedAccountCheck): CredentialAccountUi {
        val account = check.account
        val left = account?.conservativeSearchesLeft
        val active = account?.accountStatus.equals("Active", ignoreCase = true) && (left ?: 0) > 0
        val status = when {
            check.failure != null -> messageFor(check.failure)
            account?.accountStatus.equals("Active", ignoreCase = true) && (left ?: 0) <= 0 -> "Activa · sin consultas disponibles"
            account?.accountStatus.equals("Active", ignoreCase = true) -> "Activa · $left consultas conservadoras disponibles"
            else -> "Cuenta ${account?.accountStatus ?: "sin estado"}"
        }
        return CredentialAccountUi(check.credentialId, check.maskedKey, status, active, left)
    }

    fun requestFlightProbeConfirmation() {
        val current = state.value
        if (!current.credentialConfigured || current.isRunningProbe || current.isCheckingAccount) return
        viewModelScope.launch { recordFlightDiagnostic("ui_action flight_probe_confirmation_requested") }
        mutableState.value = current.copy(showFlightConfirmation = true, errorMessage = null)
    }

    fun dismissFlightProbeConfirmation() {
        viewModelScope.launch { recordFlightDiagnostic("ui_action flight_probe_confirmation_dismissed") }
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
            viewModelScope.launch { recordFlightDiagnostic("flight_probe_blocked category=INVALID_CRITERIA") }
            mutableState.value = current.copy(showFlightConfirmation = false, errorMessage = error)
            return
        }
        mutableState.value = current.copy(showFlightConfirmation = false, isRunningProbe = true, outcome = null, errorMessage = null)
        viewModelScope.launch { recordFlightDiagnostic("ui_action flight_probe_confirmed outbound=${criteria.outboundDate} return=${criteria.returnDate} direct_only=${criteria.directOnly}") }
        activeProbe = viewModelScope.launch {
            try {
                val outcome = repository.runRoundTrip(criteria)
                mutableState.value = mutableState.value.copy(outcome = outcome, account = outcome.accountAtStart)
            } catch (cancelled: CancellationException) {
                recordFlightDiagnostic("flight_probe_cancelled reason=user_request")
                mutableState.value = mutableState.value.copy(errorMessage = "La prueba fue cancelada. Las llamadas ya reservadas permanecen contabilizadas.")
            } catch (failure: ProbeFailure) {
                recordFlightDiagnostic("flight_probe_failed category=${failure.code.name}")
                mutableState.value = mutableState.value.copy(errorMessage = messageFor(failure.code))
            } catch (_: Exception) {
                recordFlightDiagnostic("flight_probe_failed category=UNEXPECTED")
                mutableState.value = mutableState.value.copy(errorMessage = "La prueba terminó con un error controlado.")
            } finally {
                val budget = runCatching { repository.localBudget() }.getOrNull()
                mutableState.value = mutableState.value.copy(isRunningProbe = false, budget = budget ?: mutableState.value.budget)
                activeProbe = null
            }
        }
    }

    fun cancelFlightProbe() {
        viewModelScope.launch { recordFlightDiagnostic("ui_action flight_probe_cancelled") }
        activeProbe?.cancel(CancellationException("User requested cancellation"))
    }

    fun clearDiagnosticLog() {
        viewModelScope.launch {
            runCatching { accountDiagnosticLog.clear() }
            mutableState.value = mutableState.value.copy(accountDiagnosticLog = "")
        }
    }

    fun clearFlightDiagnosticLog() {
        viewModelScope.launch {
            runCatching { flightDiagnosticLog.clear() }
            mutableState.value = mutableState.value.copy(flightDiagnosticLog = "")
        }
    }

    private suspend fun recordAccountDiagnostic(event: String) = recordDiagnostic(accountDiagnosticLog, event)

    private suspend fun recordFlightDiagnostic(event: String) = recordDiagnostic(flightDiagnosticLog, event)

    private suspend fun recordDiagnostic(log: DiagnosticLog, event: String) {
        try {
            log.record(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Logging must not affect app behavior.
        }
    }

    private fun messageFor(code: FailureCode): String = when (code) {
        FailureCode.NO_CREDENTIAL -> "Guarda primero la clave de SerpApi."
        FailureCode.CREDENTIAL_UNREADABLE -> "La clave cifrada no se puede leer. Elimínala e introdúcela de nuevo."
        FailureCode.NETWORK -> "No fue posible conectar. Revisa la conexión e inténtalo manualmente otra vez."
        FailureCode.INVALID_CREDENTIAL -> "SerpApi rechazó la clave. Revísala e introdúcela de nuevo."
        FailureCode.FORBIDDEN -> "SerpApi devolvió HTTP 403. La cuenta asociada no tiene permiso para esta solicitud; revisa su estado en SerpApi."
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
