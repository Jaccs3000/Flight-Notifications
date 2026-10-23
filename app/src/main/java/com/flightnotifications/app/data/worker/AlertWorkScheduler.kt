package com.flightnotifications.app.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.flightnotifications.app.data.local.DiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AlertWorkScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
    private val diagnosticLog: DiagnosticLog? = null,
) {
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureScheduled() {
        record("scheduler_registration_requested unique_name=$UNIQUE_NAME interval_hours=1 required_network=CONNECTED policy=KEEP")
        val request = PeriodicWorkRequestBuilder<FlightAlertWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .setInputData(workDataOf("trigger" to "periodic"))
            .addTag(TAG)
            .build()
        runCatching { workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request) }
            .onFailure { record("scheduler_registration_failed unique_name=$UNIQUE_NAME cause=${it.javaClass.simpleName}") }
            .getOrThrow()
    }

    /** Catch up any due alerts as soon as the user opens the app after Android deferred background work. */
    fun enqueueCatchUp() {
        record("scheduler_catch_up_requested unique_name=$CATCH_UP_NAME required_network=CONNECTED policy=KEEP")
        val request = OneTimeWorkRequestBuilder<FlightAlertWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("trigger" to "catch_up"))
            .addTag(TAG)
            .build()
        runCatching { workManager.enqueueUniqueWork(CATCH_UP_NAME, ExistingWorkPolicy.KEEP, request) }
            .onFailure { record("scheduler_catch_up_enqueue_failed unique_name=$CATCH_UP_NAME cause=${it.javaClass.simpleName}") }
            .getOrThrow()
    }

    fun scheduleStatus(): String {
        return runCatching {
            val info = workManager.getWorkInfosForUniqueWork(UNIQUE_NAME).get(5, TimeUnit.SECONDS).firstOrNull()
            record("scheduler_state unique_name=$UNIQUE_NAME state=${info?.state ?: "MISSING"} run_attempt=${info?.runAttemptCount ?: 0}")
            when (info?.state) {
                WorkInfo.State.ENQUEUED -> "Programada; Android puede aplazarla por batería o Doze."
                WorkInfo.State.RUNNING -> "Revisión automática en curso."
                WorkInfo.State.BLOCKED -> "Programación bloqueada por una dependencia del sistema."
                WorkInfo.State.CANCELLED -> "Programación cancelada; se volverá a registrar al abrir la app."
                WorkInfo.State.FAILED -> "La última ejecución del coordinador falló; WorkManager volverá a registrarlo al abrir la app."
                WorkInfo.State.SUCCEEDED -> "Coordinador registrado; próxima ejecución según Android."
                null -> "Programación pendiente de registro."
            }
        }.getOrElse {
            record("scheduler_state_read_failed unique_name=$UNIQUE_NAME cause=${it.javaClass.simpleName}")
            "No se pudo leer el estado de WorkManager (${it.javaClass.simpleName})."
        }
    }

    private fun record(event: String) {
        diagnosticLog?.let { log -> logScope.launch { runCatching { log.record(event) } } }
    }

    companion object {
        const val UNIQUE_NAME = "flight-alert-review-hourly"
        private const val CATCH_UP_NAME = "flight-alert-review-catch-up"
        private const val TAG = "flight-alert-review"
    }
}
