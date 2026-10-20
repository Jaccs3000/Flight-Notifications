package com.flightnotifications.app.data.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertRetryPolicyTest {
    @Test
    fun everyErrorBackoffRespectsTheConfiguredFrequency() {
        val hour = 60L * 60L * 1_000L
        val errors = listOf(
            "NETWORK", "RATE_LIMITED", "SERVICE_UNAVAILABLE", "BATCH_STOPPED_NETWORK",
            "BATCH_STOPPED_RATE_LIMITED", "NO_CREDENTIAL", "CREDENTIAL_UNREADABLE",
            "INVALID_CREDENTIAL", "FORBIDDEN", "ACCOUNT_INACTIVE", "ACCOUNT_INVALID",
            "QUOTA_INSUFFICIENT", "LOCAL_QUOTA", "CLOCK_UNTRUSTED", "INVALID_RESPONSE",
        )

        for (frequency in listOf(6, 8, 12, 24)) {
            for (error in errors) {
                assertEquals("$error at ${frequency}h", frequency * hour, AlertRetryPolicy.nextDelayMillis(frequency, error))
            }
        }
    }

    @Test
    fun successAndUnknownErrorsUseTheConfiguredFrequency() {
        val hour = 60L * 60L * 1_000L
        assertEquals(8 * hour, AlertRetryPolicy.nextDelayMillis(8, null))
        assertEquals(12 * hour, AlertRetryPolicy.nextDelayMillis(12, "UNEXPECTED"))
    }
}
