package com.flightnotifications.app.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/** Persistent local diagnostics. Full captures are opt-in and must redact credential values. */
class DiagnosticLog(private val file: File) {
    private val mutex = Mutex()
    private val mutableText = MutableStateFlow("")
    private val mutableStorageError = MutableStateFlow<String?>(null)
    private var loaded = false
    private var fullCapture = false

    val text: StateFlow<String> = mutableText.asStateFlow()
    /** Last disk error, exposed so a full search capture is never reported as safely saved when it was not. */
    val storageError: StateFlow<String?> = mutableStorageError.asStateFlow()

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        val read = withContext(Dispatchers.IO) { runCatching { if (file.isFile) file.readText(Charsets.UTF_8) else "" } }
        mutableStorageError.value = read.exceptionOrNull()?.javaClass?.simpleName
        val contents = read.getOrDefault("")
        fullCapture = contents.startsWith(CAPTURE_HEADER)
        mutableText.value = if (fullCapture) contents.removePrefix(CAPTURE_HEADER).trimStart('\n') else keepRecentLines(contents)
        loaded = true
    }

    suspend fun record(event: String) = mutex.withLock {
        ensureLoaded()
        val safeEvent = redact(event.replace('\n', ' ').replace('\r', ' '))
        val line = "${Instant.now()} | $safeEvent"
        appendLocked(line)
    }

    /** Starts a new, unbounded capture. The prior capture is discarded by design. */
    suspend fun beginCapture(event: String) = mutex.withLock {
        val safeEvent = redact(event.replace('\n', ' ').replace('\r', ' '))
        val line = "${Instant.now()} | $safeEvent"
        val contents = "$CAPTURE_HEADER\n$line"
        val write = withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(contents, Charsets.UTF_8)
            }
        }
        mutableStorageError.value = write.exceptionOrNull()?.javaClass?.simpleName
        loaded = true
        fullCapture = true
        mutableText.value = line
    }

    /** Appends a full payload without flattening or truncating it, for request/response capture. */
    suspend fun recordPayload(event: String, payload: String) = mutex.withLock {
        ensureLoaded()
        val safeEvent = redact(event.replace('\n', ' ').replace('\r', ' '))
        val safePayload = redact(payload)
        val line = if (safePayload.isBlank()) "${Instant.now()} | $safeEvent" else "${Instant.now()} | $safeEvent\n$safePayload"
        appendLocked(line)
    }

    suspend fun clear() = mutex.withLock {
        val deletion = withContext(Dispatchers.IO) {
            runCatching { if (file.exists() && !file.delete()) error("DELETE_FAILED") }
        }
        mutableStorageError.value = deletion.exceptionOrNull()?.javaClass?.simpleName
        loaded = true
        fullCapture = false
        mutableText.value = ""
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        val read = withContext(Dispatchers.IO) { runCatching { if (file.isFile) file.readText(Charsets.UTF_8) else "" } }
        mutableStorageError.value = read.exceptionOrNull()?.javaClass?.simpleName
        val contents = read.getOrDefault("")
        fullCapture = contents.startsWith(CAPTURE_HEADER)
        mutableText.value = if (fullCapture) contents.removePrefix(CAPTURE_HEADER).trimStart('\n') else keepRecentLines(contents)
        loaded = true
    }

    private suspend fun appendLocked(entry: String) {
        val existing = mutableText.value
        val updated = if (fullCapture) {
            listOf(existing, entry).filter(String::isNotBlank).joinToString("\n")
        } else {
            keepRecentLines(listOf(existing, entry).filter(String::isNotBlank).joinToString("\n"))
        }
        val write = withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                if (fullCapture) file.appendText("\n$entry", Charsets.UTF_8)
                else file.writeText(updated, Charsets.UTF_8)
            }
        }
        mutableStorageError.value = write.exceptionOrNull()?.javaClass?.simpleName
        mutableText.value = updated
    }

    private fun keepRecentLines(contents: String): String =
        contents.lineSequence().filter(String::isNotBlank).toList().takeLast(MAX_LINES).joinToString("\n")

    private fun redact(value: String): String {
        val jsonSafe = JSON_SECRET.replace(value) { match -> "${match.groupValues[1]}[REDACTED]${match.groupValues[2]}" }
        val personalDataSafe = PERSONAL_JSON.replace(jsonSafe) { match -> "${match.groupValues[1]}[REDACTED]${match.groupValues[2]}" }
        val personalParameterSafe = PERSONAL_PARAMETER.replace(personalDataSafe) { match -> "${match.groupValues[1]}[REDACTED]" }
        val endpointSafe = SERP_SEARCH_URL.replace(personalParameterSafe) { match -> "${match.groupValues[1]}[REDACTED]/" }
        val authorizationSafe = AUTHORIZATION_SECRET.replace(endpointSafe) { match -> "${match.groupValues[1]}[REDACTED]" }
        return PARAMETER_SECRET.replace(authorizationSafe) { match -> "${match.groupValues[1]}[REDACTED]" }
    }

    private companion object {
        const val MAX_LINES = 300
        const val CAPTURE_HEADER = "# FLIGHT_SEARCH_FULL_CAPTURE_V1"
        private const val SECRET_FIELD = "(?<![A-Za-z0-9_])(?:api[_-]?key|(?:api|access|refresh|id|auth|booking|departure)?[_-]?token|password|client[_-]?secret|secret)"
        val JSON_SECRET = Regex("(?i)([\"']$SECRET_FIELD[\"']\\s*:\\s*[\"'])[^\"']*([\"'])")
        val PERSONAL_JSON = Regex("(?i)([\"'](?:account_email|email|account_id|user_id)[\"']\\s*:\\s*[\"'])[^\"']*([\"'])")
        val PERSONAL_PARAMETER = Regex("(?i)([\"']?(?:account_email|email|account_id|user_id)[\"']?\\s*=\\s*(?:[\"']?))[^&\\s,}\\]\\\"']+")
        val SERP_SEARCH_URL = Regex("(?i)(https?://serpapi\\.com/searches/)[^/\\s\"']+/")
        val AUTHORIZATION_SECRET = Regex("(?i)([\"']?authorization[\"']?\\s*(?:=|:)\\s*)[^\\r\\n,&}]+")
        val PARAMETER_SECRET = Regex("(?i)([\"']?$SECRET_FIELD[\"']?\\s*(?:=|:)\\s*)(?:[\"']?)[^&\\s,}\\]\\\"']+")
    }
}
