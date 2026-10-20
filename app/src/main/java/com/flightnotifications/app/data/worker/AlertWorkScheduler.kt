package com.flightnotifications.app.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AlertWorkScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
) {

    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<FlightAlertWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun scheduleStatus(): String {
        return runCatching {
            val info = workManager.getWorkInfosForUniqueWork(UNIQUE_NAME).get(5, TimeUnit.SECONDS).firstOrNull()
            when (info?.state) {
                WorkInfo.State.ENQUEUED -> "Programada; Android puede aplazarla por batería o Doze."
                WorkInfo.State.RUNNING -> "Revisión automática en curso."
                WorkInfo.State.BLOCKED -> "Programación bloqueada por una dependencia del sistema."
                WorkInfo.State.CANCELLED -> "Programación cancelada; se volverá a registrar al abrir la app."
                WorkInfo.State.FAILED -> "La última ejecución del coordinador falló; WorkManager volverá a registrarlo al abrir la app."
                WorkInfo.State.SUCCEEDED -> "Coordinador registrado; próxima ejecución según Android."
                null -> "Programación pendiente de registro."
            }
        }.getOrElse { "No se pudo leer el estado de WorkManager (${it.javaClass.simpleName})." }
    }

    companion object {
        const val UNIQUE_NAME = "flight-alert-review-hourly"
        private const val TAG = "flight-alert-review"
    }
}
