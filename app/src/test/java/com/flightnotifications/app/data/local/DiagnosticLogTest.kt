package com.flightnotifications.app.data.local

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogTest {
    @Test
    fun logPersistsAcrossInstancesAndRedactsCredentialAndTokens() = runBlocking {
        val directory = Files.createTempDirectory("flight-diagnostics-test").toFile()
        val file = directory.resolve("diagnostics.log")
        try {
            DiagnosticLog(file).record(
                "request api_key=FAKE_SECRET departure_token=FAKE_TOKEN booking_token:FAKE_BOOKING",
            )

            val loaded = DiagnosticLog(file)
            loaded.load()
            val text = loaded.text.value
            assertTrue(text.contains("request"))
            assertTrue(text.contains("api_key=[REDACTED]"))
            assertTrue(text.contains("departure_token=[REDACTED]"))
            assertTrue(text.contains("booking_token:[REDACTED]"))
            assertFalse(text.contains("FAKE_SECRET"))
            assertFalse(text.contains("FAKE_TOKEN"))
            assertFalse(text.contains("FAKE_BOOKING"))

            loaded.clear()
            assertFalse(file.exists())
            assertTrue(loaded.text.value.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
