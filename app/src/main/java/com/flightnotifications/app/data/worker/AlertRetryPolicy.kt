package com.flightnotifications.app.data.worker

/** Keeps the next automatic attempt no earlier than the alert's configured frequency. */
object AlertRetryPolicy {
    private const val HOUR_MILLIS = 60L * 60L * 1000L

    fun nextDelayMillis(frequencyHours: Int, error: String?): Long {
        val frequency = frequencyHours.coerceAtLeast(1) * HOUR_MILLIS
        val backoff = when (error) {
            "NETWORK", "RATE_LIMITED", "SERVICE_UNAVAILABLE", "BATCH_STOPPED_NETWORK", "BATCH_STOPPED_RATE_LIMITED" -> 2 * HOUR_MILLIS
            "NO_CREDENTIAL", "CREDENTIAL_UNREADABLE", "INVALID_CREDENTIAL", "FORBIDDEN", "ACCOUNT_INACTIVE", "ACCOUNT_INVALID" -> 6 * HOUR_MILLIS
            "QUOTA_INSUFFICIENT", "LOCAL_QUOTA", "CLOCK_UNTRUSTED" -> 6 * HOUR_MILLIS
            else -> frequency
        }
        return maxOf(frequency, backoff)
    }
}
