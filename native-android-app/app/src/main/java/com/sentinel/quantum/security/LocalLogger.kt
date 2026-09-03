package com.sentinel.quantum.security

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal de sécurité local, borné en taille et sans émission réseau.
 */
class LocalLogger(private val context: Context) {

    private companion object {
        const val MAX_LOG_BYTES = 1024L * 1024L
        const val MAX_LOG_LINES = 2000
        const val MAX_MESSAGE_LENGTH = 2000
        const val MAX_TAG_LENGTH = 64
    }

    private val logFile: File by lazy { File(context.filesDir, "sentinel_security.log") }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun log(level: LogLevel, tag: String, message: String) {
        val safeTag = sanitize(tag, MAX_TAG_LENGTH)
        val safeMessage = sanitize(message, MAX_MESSAGE_LENGTH)
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] [${level.name}] [$safeTag] $safeMessage\n"

        try {
            rotateIfNeeded(logEntry.toByteArray(StandardCharsets.UTF_8).size.toLong())
            logFile.appendText(logEntry, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            // Le journal ne doit jamais interrompre une fonction de sécurité.
        }
    }

    fun getLogs(): List<LogEntry> = try {
        if (!logFile.exists() || logFile.length() > MAX_LOG_BYTES) {
            emptyList()
        } else {
            logFile.readLines(StandardCharsets.UTF_8)
                .takeLast(MAX_LOG_LINES)
                .filter(String::isNotBlank)
                .mapNotNull(::parseLogEntry)
                .reversed()
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun clearLogs() {
        try {
            if (logFile.exists()) logFile.delete()
        } catch (_: Exception) {
            // Best effort only.
        }
    }

    private fun rotateIfNeeded(incomingBytes: Long) {
        if (logFile.exists() && logFile.length() + incomingBytes > MAX_LOG_BYTES) {
            val backup = File(context.filesDir, "sentinel_security.log.1")
            if (backup.exists()) backup.delete()
            logFile.renameTo(backup)
        }
    }

    private fun sanitize(value: String, maxLength: Int): String =
        value.replace(Regex("[\\r\\n\\u0000]"), " ").take(maxLength)

    private fun parseLogEntry(line: String): LogEntry? = try {
        val parts = line.split("] [", limit = 4)
        if (parts.size != 4) return null
        val timestamp = parts[0].removePrefix("[")
        val level = parts[1].trimEnd(']')
        val tag = parts[2].trimEnd(']')
        val message = parts[3].trimEnd(']')
        LogEntry(timestamp, LogLevel.valueOf(level), tag, message)
    } catch (_: Exception) {
        null
    }

    enum class LogLevel { INFO, WARNING, ERROR, SECURITY }

    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String
    )
}
