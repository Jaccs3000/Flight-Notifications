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
                "request api_key=FAKE_SECRET departure_token=FAKE_TOKEN booking_token:FAKE_BOOKING account_email=jesus@example.com",
            )

            val loaded = DiagnosticLog(file)
            loaded.load()
            val text = loaded.text.value
            assertTrue(text.contains("request"))
            assertTrue(text.contains("api_key=[REDACTED]"))
            assertTrue(text.contains("departure_token=[REDACTED]"))
            assertTrue(text.contains("booking_token:[REDACTED]"))
            assertTrue(text.contains("account_email=[REDACTED]"))
            assertFalse(text.contains("FAKE_SECRET"))
            assertFalse(text.contains("FAKE_TOKEN"))
            assertFalse(text.contains("FAKE_BOOKING"))
            assertFalse(text.contains("jesus@example.com"))

            loaded.clear()
            assertFalse(file.exists())
            assertTrue(loaded.text.value.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aSearchCaptureReplacesPreviousLogAndKeepsFullMultilinePayloadSafely() = runBlocking {
        val directory = Files.createTempDirectory("flight-search-capture-test").toFile()
        val file = directory.resolve("diagnostics.log")
        try {
            val log = DiagnosticLog(file)
            log.record("previous app event")
            log.beginCapture("search started api_key=FAKE_API_KEY")
            val payload = buildString {
                appendLine("{\"departure_token\":\"FAKE_DEPARTURE\", \"booking_token\":\"FAKE_BOOKING\",")
                appendLine("  \"account_email\": \"jesus@example.com\", \"account_id\": \"PRIVATE_ACCOUNT_ID\",")
                repeat(350) { appendLine("  \"result_$it\": \"observed\",") }
                append("  \"authorization\": \"Bearer FAKE_AUTH_TOKEN\"}")
            }
            log.recordPayload("HTTP response", payload)

            val loaded = DiagnosticLog(file)
            loaded.load()
            val text = loaded.text.value
            assertTrue(text.contains("search started"))
            assertTrue(text.contains("HTTP response"))
            assertFalse(text.contains("previous app event"))
            assertTrue(text.contains("result_349"))
            assertTrue(text.contains("departure_token\":[REDACTED]"))
            assertTrue(text.contains("booking_token\":[REDACTED]"))
            assertTrue(text.contains("authorization: [REDACTED]") || text.contains("authorization\": [REDACTED]"))
            assertFalse(text.contains("FAKE_API_KEY"))
            assertFalse(text.contains("FAKE_DEPARTURE"))
            assertFalse(text.contains("FAKE_BOOKING"))
            assertFalse(text.contains("FAKE_AUTH_TOKEN"))
            assertTrue(text.contains("account_email\": \"[REDACTED]\""))
            assertTrue(text.contains("account_id\": \"[REDACTED]\""))
            assertFalse(text.contains("jesus@example.com"))
            assertFalse(text.contains("PRIVATE_ACCOUNT_ID"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun opaqueSerpApiSearchEndpointTokensAreRedacted() = runBlocking {
        val directory = Files.createTempDirectory("flight-url-redaction-test").toFile()
        val file = directory.resolve("diagnostics.log")
        try {
            val log = DiagnosticLog(file)
            log.record("endpoint https://serpapi.com/searches/OPAQUE_SECRET_ID/abcdef123.json")
            assertFalse(log.text.value.contains("OPAQUE_SECRET_ID"))
            assertTrue(log.text.value.contains("https://serpapi.com/searches/[REDACTED]/abcdef123.json"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedDiskWritesAreVisibleWithoutFailingTheCaller() = runBlocking {
        val directory = Files.createTempDirectory("flight-log-write-error-test").toFile()
        val notDirectory = directory.resolve("not-a-directory")
        notDirectory.writeText("block")
        try {
            val log = DiagnosticLog(notDirectory.resolve("diagnostics.log"))
            log.beginCapture("search started")
            assertTrue(log.text.value.contains("search started"))
            assertTrue(log.storageError.value != null)
        } finally {
            directory.deleteRecursively()
        }
    }
}
