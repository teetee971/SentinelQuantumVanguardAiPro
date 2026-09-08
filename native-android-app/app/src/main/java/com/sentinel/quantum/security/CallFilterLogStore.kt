package com.sentinel.quantum.security

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bounded, app-private history of local call-screening decisions. Numbers are masked before
 * being persisted; no raw phone number, contact, or full call log is stored.
 */
class CallFilterLogStore(private val context: Context) {

    private val historyFile: File by lazy { File(context.filesDir, "sentinel_call_filter_history.log") }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun record(
        action: CallRuleEngine.Action,
        reason: String,
        source: CallRuleEngine.RuleSource,
        normalizedNumber: String?,
        now: Long = System.currentTimeMillis()
    ) {
        val entry = "${dateFormat.format(Date(now))}|${action.name}|${sanitize(reason)}|" +
            "${source.name}|${maskNumber(normalizedNumber)}\n"
        try {
            rotateIfNeeded(entry.toByteArray(StandardCharsets.UTF_8).size.toLong())
            historyFile.appendText(entry, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            // Recording history must never interrupt call screening.
        }
    }

    fun history(): List<Entry> = try {
        if (!historyFile.exists() || historyFile.length() > MAX_HISTORY_BYTES) {
            emptyList()
        } else {
            historyFile.readLines(StandardCharsets.UTF_8)
                .takeLast(MAX_HISTORY_ENTRIES)
                .filter(String::isNotBlank)
                .mapNotNull(::parseEntry)
                .reversed()
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun clear(): Boolean = try {
        !historyFile.exists() || historyFile.delete()
    } catch (_: Exception) {
        false
    }

    private fun rotateIfNeeded(incomingBytes: Long) {
        if (historyFile.exists() && historyFile.length() + incomingBytes > MAX_HISTORY_BYTES) {
            val backup = File(context.filesDir, "sentinel_call_filter_history.log.1")
            if (backup.exists()) backup.delete()
            historyFile.renameTo(backup)
        }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\r\\n\\u0000|]"), " ").take(MAX_REASON_LENGTH)

    private fun parseEntry(line: String): Entry? = try {
        val parts = line.split("|", limit = 5)
        if (parts.size != 5) return null
        Entry(
            timestamp = parts[0],
            action = CallRuleEngine.Action.valueOf(parts[1]),
            reason = parts[2],
            source = CallRuleEngine.RuleSource.valueOf(parts[3]),
            maskedNumber = parts[4]
        )
    } catch (_: Exception) {
        null
    }

    data class Entry(
        val timestamp: String,
        val action: CallRuleEngine.Action,
        val reason: String,
        val source: CallRuleEngine.RuleSource,
        val maskedNumber: String
    )

    companion object {
        const val MAX_HISTORY_BYTES = 512L * 1024L
        const val MAX_HISTORY_ENTRIES = 500
        private const val MAX_REASON_LENGTH = 128

        /** Keeps only enough of the number to be recognisable, redacting the rest. */
        fun maskNumber(normalizedNumber: String?): String {
            val value = normalizedNumber?.takeIf { it.isNotBlank() } ?: return "INCONNU"
            val digits = value.filter(Char::isDigit)
            if (digits.length < 4) return "•".repeat(digits.length.coerceAtLeast(1))
            val prefix = if (value.startsWith('+')) "+" else ""
            return "$prefix${digits.take(2)}${"•".repeat((digits.length - 4).coerceAtLeast(0))}${digits.takeLast(2)}"
        }
    }
}
