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
        const val EXPORT_DIR = "sentinel_log_export"
        const val EXPORT_FILE_NAME = "sentinel_security_export.txt"
    }

    private val logFile: File by lazy { File(context.filesDir, "sentinel_security.log") }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun log(level: LogLevel, tag: String, message: String) {
        val safeTag = sanitize(tag, MAX_TAG_LENGTH)
        val safeMessage = sanitize(SensitiveLogRedactor.redact(message), MAX_MESSAGE_LENGTH)
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

    /**
     * Writes the currently visible (already sanitized and bounded) log entries to a dedicated
     * cache sub-directory, for sharing via [androidx.core.content.FileProvider]. No network
     * access is performed. Returns null if there is nothing to export or the write fails.
     */
    fun exportSanitizedCopy(): File? {
        val entries = getLogs()
        if (entries.isEmpty()) return null
        return try {
            val exportDir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            val exportFile = File(exportDir, EXPORT_FILE_NAME)
            val content = entries.asReversed().joinToString("\n") { entry ->
                "[${entry.timestamp}] [${entry.level.name}] [${entry.tag}] ${entry.message}"
            }
            exportFile.writeText(content, StandardCharsets.UTF_8)
            exportFile
        } catch (_: Exception) {
            null
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

internal object SensitiveLogRedactor {
    private const val REDACTED = "[REDACTED]"
    private val patterns = listOf(
        Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----.*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("\\bgh[pousr]_[A-Za-z0-9_]{20,}\\b"),
        Regex("\\bAIza[A-Za-z0-9_-]{30,}\\b"),
        Regex("\\b[0-9]{6,12}:[A-Za-z0-9_-]{30,}\\b"),
        Regex("(?i)\\b(?:authorization|api[_-]?key|access[_-]?key|client[_-]?secret|token|secret|password|signature(?:_hex)?|payload_hex)\\s*[:=]\\s*(?:Bearer\\s+)?[^\\s,;]+")
    )

    fun redact(value: String): String = patterns.fold(value) { result, pattern ->
        pattern.replace(result, REDACTED)
    }
}
