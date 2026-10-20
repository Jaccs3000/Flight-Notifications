package com.flightnotifications.app.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AlertWorkSchedulerTest {
    @Test
    fun repeatedRegistrationKeepsOnePeriodicConnectedCoordinator() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build(),
        )
        val workManager = WorkManager.getInstance(context)
        val scheduler = AlertWorkScheduler(context, workManager)

        scheduler.ensureScheduled()
        scheduler.ensureScheduled()

        val work = workManager.getWorkInfosForUniqueWork(AlertWorkScheduler.UNIQUE_NAME)
            .get(5, TimeUnit.SECONDS)

        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertEquals(NetworkType.CONNECTED, work.single().constraints.requiredNetworkType)
        assertTrue(work.single().tags.contains("flight-alert-review"))
    }
}
