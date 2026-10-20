package com.flightnotifications.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightnotifications.app.data.local.FlightAlertEntity
import com.flightnotifications.app.data.repository.AlertWithResult
import com.flightnotifications.app.di.AppContainer
import com.flightnotifications.app.domain.model.AlertSearchResult
import com.flightnotifications.app.domain.validation.FlightAlertInputValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

data class AlertInput(
    val id: String?,
    val name: String,
    val origin: String,
    val destination: String,
    val outboundDate: LocalDate,
    val returnDate: LocalDate,
    val maximumCop: Long,
    val frequencyHours: Int,
    val directOnly: Boolean,
)

data class FlightAlertsUiState(
    val alerts: List<AlertWithResult> = emptyList(),
    val notifications: List<com.flightnotifications.app.data.local.SentNotificationEntity> = emptyList(),
    val reviewConfirmationId: String? = null,
    val selectedAlertId: String? = null,
    val deletingAlertId: String? = null,
    val showClearNotificationsConfirmation: Boolean = false,
    val runningAlertId: String? = null,
    val message: String? = null,
    val error: String? = null,
    val schedulingStatus: String = "Leyendo estado de programación…",
)

class FlightAlertsViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(FlightAlertsUiState())
    val state: StateFlow<FlightAlertsUiState> = mutableState.asStateFlow()
    val airlineCatalog get() = container.airlineCatalog
    fun notificationsAllowed() = container.notificationDispatcher.notificationsAllowed()
    private var activeReview: Job? = null

    init {
        viewModelScope.launch {
            container.flightAlertRepository.alerts.collect { list ->
                mutableState.value = mutableState.value.copy(alerts = list)
            }
        }
        viewModelScope.launch {
            container.flightAlertRepository.notifications.collect { list ->
                mutableState.value = mutableState.value.copy(notifications = list)
            }
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val status = container.alertWorkScheduler.scheduleStatus()
            mutableState.value = mutableState.value.copy(schedulingStatus = status)
        }
    }

    fun save(input: AlertInput, onSaved: () -> Unit = {}) {
        validate(input)?.let { error -> mutableState.value = mutableState.value.copy(error = error, message = null); return }
        viewModelScope.launch {
            runCatching {
                container.executionMutex.withLock {
                    val existing = input.id?.let { container.flightAlertRepository.find(it) }
                    val candidate = if (existing == null) {
                        container.flightAlertRepository.newAlert(
                            name = input.name,
                            origin = input.origin,
                            destination = input.destination,
                            outbound = input.outboundDate,
                            returning = input.returnDate,
                            maximum = input.maximumCop,
                            frequency = input.frequencyHours,
                            direct = input.directOnly,
                            enabled = false,
                        )
                    } else {
                        existing.copy(
                            name = input.name.trim(),
                            originIata = input.origin.trim().uppercase(),
                            destinationIata = input.destination.trim().uppercase(),
                            outboundDate = input.outboundDate.toString(),
                            returnDate = input.returnDate.toString(),
                            maxTotalCop = input.maximumCop,
                            frequencyHours = input.frequencyHours,
                            directOnly = input.directOnly,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                    container.flightAlertRepository.save(candidate)
                }
            }.onSuccess { saved ->
                val message = if (input.id == null) {
                    "Alerta guardada pausada. Actívala cuando quieras programar revisiones."
                } else if (saved.enabled) {
                    "Alerta actualizada y activa. Si cambiaste ruta, fechas, escalas o máximo, se necesita una revisión nueva antes de usar el resultado anterior."
                } else {
                    "Alerta actualizada y pausada."
                }
                mutableState.value = mutableState.value.copy(message = message, error = null)
                container.flightDiagnosticLog.record("ui_action alert_saved alert_id=${saved.id.take(8)} criteria_version=${saved.criteriaVersion} policy_version=${saved.notificationPolicyVersion}")
                refreshSchedule()
                onSaved()
            }.onFailure {
                mutableState.value = mutableState.value.copy(error = "No fue posible guardar la alerta.", message = null)
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val changed = container.executionMutex.withLock {
                val alert = container.flightAlertRepository.find(id) ?: return@withLock false
                if (enabled && LocalDate.now(COLOMBIA_ZONE).isAfter(LocalDate.parse(alert.outboundDate))) {
                    mutableState.value = mutableState.value.copy(error = "La fecha de ida ya venció; edita la alerta antes de activarla.", message = null)
                    return@withLock false
                }
                container.flightAlertRepository.setEnabled(id, enabled)
                true
            }
            if (!changed) return@launch
            mutableState.value = mutableState.value.copy(
                message = if (enabled) "Alerta activada. Android realizará las revisiones según la disponibilidad del sistema." else "Alerta pausada.",
                error = null,
            )
            container.flightDiagnosticLog.record("ui_action alert_enabled_changed alert_id=${id.take(8)} enabled=$enabled")
            container.alertWorkScheduler.ensureScheduled()
            refreshSchedule()
        }
    }

    fun requestReview(id: String) {
        if (mutableState.value.runningAlertId != null) return
        mutableState.value = mutableState.value.copy(reviewConfirmationId = id, error = null, message = null)
    }

    fun dismissReviewConfirmation() {
        mutableState.value = mutableState.value.copy(reviewConfirmationId = null)
    }

    fun confirmReview() {
        val id = mutableState.value.reviewConfirmationId ?: return
        val alert = mutableState.value.alerts.firstOrNull { it.alert.id == id }?.alert ?: return
        mutableState.value = mutableState.value.copy(reviewConfirmationId = null, runningAlertId = id, error = null, message = null)
        activeReview = viewModelScope.launch {
            var completedResult: AlertSearchResult? = null
            try {
                val outcomes = container.serpApiRepository.runAlertBatch(
                    alerts = listOf(alert),
                    maximumGets = com.flightnotifications.app.data.repository.SerpApiRepository.MAX_GETS_PER_ALERT,
                    afterResults = { results ->
                        results.forEach { result ->
                            completedResult = result
                            val saved = container.flightAlertRepository.saveOutcome(alert.id, result, alert)
                            if (saved) {
                                if (result.candidatesConsidered > 0) container.flightAlertRepository.advanceCandidateRotation(alert)
                                val latest = container.flightAlertRepository.find(alert.id)
                                if (alert.enabled && latest?.enabled == true &&
                                    latest.criteriaVersion == alert.criteriaVersion &&
                                    latest.notificationPolicyVersion == alert.notificationPolicyVersion &&
                                    result.status == "VERIFICADO"
                                ) {
                                    val dispatch = container.notificationDispatcher.dispatchIfEligible(latest, result, container.flightAlertRepository)
                                    container.flightDiagnosticLog.record("manual_notification_dispatch alert_id=${id.take(8)} result=$dispatch")
                                } else if (alert.enabled && result.status == "VERIFICADO" && !container.notificationDispatcher.notificationsAllowed()) {
                                    container.flightDiagnosticLog.record("manual_notification_suppressed alert_id=${id.take(8)} cause=permission_or_channel_disabled")
                                }
                            }
                        }
                    },
                )
                val result = completedResult ?: outcomes.firstOrNull()
                container.flightAlertRepository.recordManualAttempt(alert, result?.attemptAtMillis ?: System.currentTimeMillis())
                mutableState.value = mutableState.value.copy(
                    message = result?.let(::resultMessage) ?: "La revisión finalizó sin resultado.",
                    error = if (result?.status == "ERROR") result.error?.let(::errorMessage) else null,
                )
                container.flightDiagnosticLog.record("ui_action manual_alert_review_complete alert_id=${id.take(8)} status=${result?.status ?: "UNKNOWN"} flight_gets=${result?.flightGetsStarted ?: 0}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                container.flightDiagnosticLog.record("manual_alert_review_failed alert_id=${id.take(8)} category=UNEXPECTED")
                mutableState.value = mutableState.value.copy(error = "No se pudo completar la revisión manual. Revisa el registro de vuelos.", message = null)
            } finally {
                mutableState.value = mutableState.value.copy(runningAlertId = null)
                activeReview = null
            }
        }
    }

    fun requestDelete(id: String) { mutableState.value = mutableState.value.copy(deletingAlertId = id) }
    fun dismissDelete() { mutableState.value = mutableState.value.copy(deletingAlertId = null) }
    fun confirmDelete() {
        val id = mutableState.value.deletingAlertId ?: return
        mutableState.value = mutableState.value.copy(deletingAlertId = null)
        viewModelScope.launch {
            container.executionMutex.withLock { container.flightAlertRepository.remove(id) }
            container.flightDiagnosticLog.record("ui_action alert_deleted alert_id=${id.take(8)}")
            mutableState.value = mutableState.value.copy(message = "Alerta eliminada. El presupuesto global de consultas se conserva.", error = null)
        }
    }

    fun requestClearNotifications() {
        mutableState.value = mutableState.value.copy(showClearNotificationsConfirmation = true)
    }
    fun dismissClearNotifications() {
        mutableState.value = mutableState.value.copy(showClearNotificationsConfirmation = false)
    }
    fun confirmClearNotifications() {
        mutableState.value = mutableState.value.copy(showClearNotificationsConfirmation = false)
        viewModelScope.launch {
            container.executionMutex.withLock {
                container.notificationDispatcher.cancelAllVisible()
                container.flightAlertRepository.clearVisibleNotifications()
            }
            container.flightDiagnosticLog.record("ui_action visible_notifications_cleared future_lowest_fare_notifications_unchanged=true")
        }
    }

    fun checkScheduling() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            container.alertWorkScheduler.ensureScheduled()
            val status = container.alertWorkScheduler.scheduleStatus()
            mutableState.value = mutableState.value.copy(schedulingStatus = status)
            container.flightDiagnosticLog.record("scheduler_diagnostic_check result=${status.replace(Regex("[^A-Za-z0-9 _-]"), " ").take(100)} api_calls=0")
        }
    }

    fun setNotificationOpenAlert(id: String?) {
        if (id == null) return
        mutableState.value = mutableState.value.copy(selectedAlertId = id, message = "Alerta seleccionada: ${id.take(8)}")
    }

    fun clearSelectedAlert() { mutableState.value = mutableState.value.copy(selectedAlertId = null) }
    fun notificationPermissionDenied() {
        mutableState.value = mutableState.value.copy(error = "Activa las notificaciones de Flight Notifications en Ajustes para habilitar avisos de ofertas.", message = null)
    }

    private fun validate(input: AlertInput): String? {
        return FlightAlertInputValidator.validate(
            name = input.name,
            origin = input.origin,
            destination = input.destination,
            outboundDate = input.outboundDate,
            returnDate = input.returnDate,
            maximumCop = input.maximumCop,
            frequencyHours = input.frequencyHours,
            today = LocalDate.now(COLOMBIA_ZONE),
            containsDomesticAirport = container.airportCatalog::containsDomesticAirport,
        )
    }

    private fun resultMessage(result: AlertSearchResult): String = when (result.status) {
        "VERIFICADO" -> "Tarifa de ida y vuelta verificada para ${result.offers.size} combinación(es). GET de vuelos: ${result.flightGetsStarted}; exploración ${if (result.explorationLimited) "parcial" else "completa dentro de las opciones recibidas"}."
        "NO_VERIFICADO" -> "Se recibieron itinerarios, pero la fuente no permitió verificar un total de ida y vuelta. GET: ${result.flightGetsStarted}."
        "SIN_RESULTADOS_DEVUELTOS" -> "SerpApi no devolvió opciones para esta consulta. Esto no confirma que no existan vuelos."
        "EXPIRED" -> "La fecha de ida de esta alerta venció; no se hizo una consulta."
        else -> result.error?.let(::errorMessage) ?: "La revisión no produjo una tarifa verificable."
    }

    private fun errorMessage(code: String): String = when (code.removePrefix("BATCH_STOPPED_")) {
        "NO_CREDENTIAL" -> "Guarda la clave de SerpApi en Configuración para consultar vuelos."
        "CREDENTIAL_UNREADABLE" -> "La clave cifrada no se puede leer; vuelve a guardarla."
        "INVALID_CREDENTIAL" -> "SerpApi rechazó la clave. Revísala en Configuración."
        "FORBIDDEN" -> "SerpApi devolvió HTTP 403; revisa permisos y estado de cuenta."
        "ACCOUNT_INACTIVE" -> "La cuenta de SerpApi no está activa."
        "ACCOUNT_INVALID" -> "La respuesta de cuenta no permite calcular capacidad con seguridad."
        "NETWORK" -> "No fue posible conectar con SerpApi. No se reintentó esta consulta."
        "RATE_LIMITED" -> "SerpApi aplicó un límite temporal. La app esperará antes del siguiente turno."
        "SERVICE_UNAVAILABLE" -> "SerpApi no está disponible; la app esperará antes del siguiente turno."
        "QUOTA_INSUFFICIENT" -> "La capacidad oficial no alcanza para iniciar la consulta mínima de ida y vuelta."
        "LOCAL_QUOTA" -> "El límite local de GET en 30 días alcanzó su tope."
        "CLOCK_UNTRUSTED" -> "La hora del dispositivo no es confiable; las consultas quedaron bloqueadas."
        "INVALID_CRITERIA", "EXPIRED" -> "Los criterios de esta alerta ya no son válidos o vencieron."
        "NO_VERIFIED_TOTAL" -> "La respuesta no confirmó una tarifa total compatible; no se enviará aviso."
        else -> "La consulta falló ($code). Revisa el registro de vuelos para el detalle técnico seguro."
    }

    private fun refreshSchedule() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mutableState.value = mutableState.value.copy(schedulingStatus = container.alertWorkScheduler.scheduleStatus())
        }
    }

    companion object {
        val FREQUENCIES = FlightAlertInputValidator.ALLOWED_FREQUENCIES
        private val COLOMBIA_ZONE = ZoneId.of("America/Bogota")
    }
}
