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

/** Persistent local diagnostics. Callers must log structured facts, never response bodies or secrets. */
class DiagnosticLog(private val file: File) {
    private val mutex = Mutex()
    private val mutableText = MutableStateFlow("")
    private var loaded = false

    val text: StateFlow<String> = mutableText.asStateFlow()

    suspend fun load() = mutex.withLock {
        val contents = withContext(Dispatchers.IO) {
            runCatching { if (file.isFile) file.readText(Charsets.UTF_8) else "" }.getOrDefault("")
        }
        mutableText.value = keepRecentLines(contents)
        loaded = true
    }

    suspend fun record(event: String) = mutex.withLock {
        val existing = if (loaded) mutableText.value else {
            withContext(Dispatchers.IO) {
                runCatching { if (file.isFile) file.readText(Charsets.UTF_8) else "" }.getOrDefault("")
            }
        }
        val safeEvent = redact(event.replace('\n', ' ').replace('\r', ' '))
        val line = "${Instant.now()} | $safeEvent"
        val updated = keepRecentLines(listOf(existing, line).filter(String::isNotBlank).joinToString("\n"))
        withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(updated, Charsets.UTF_8)
            }
        }
        loaded = true
        mutableText.value = updated
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { runCatching { file.delete() } }
        loaded = true
        mutableText.value = ""
    }

    private fun keepRecentLines(contents: String): String =
        contents.lineSequence().filter(String::isNotBlank).toList().takeLast(MAX_LINES).joinToString("\n")

    private fun redact(value: String): String = SECRET_PARAMETER.replace(value) { match ->
        "${match.groupValues[1]}${match.groupValues[2]}[REDACTED]"
    }

    private companion object {
        const val MAX_LINES = 300
        val SECRET_PARAMETER = Regex("(?i)(api_key|departure_token|booking_token|access_token)(\\s*[=:]\\s*)[^&\\s,]+")
    }
}
