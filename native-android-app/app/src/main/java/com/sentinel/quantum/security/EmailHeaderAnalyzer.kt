package com.sentinel.quantum.security

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object EmailHeaderAnalyzer {
    private const val MAX_HEADERS = 200
    private const val MAX_LINE_LENGTH = 8_192
    private val HEADER_NAME = Regex("[a-z0-9-]{1,78}")
    private val FROM_DISPLAY = Regex("^\\s*\"([^\"]{1,80})\"\\s*<[^>]+>")
    private val GENERIC_FORGED_NAMES = setOf("support", "security", "sécurité", "service client", "admin", "assistance", "impots", "ameli", "paypal")

    fun analyze(headers: Map<String, List<String>>): EmailHeaderReport {
        val anomalies = mutableListOf<HeaderAnomaly>()
        val received = headers["received"].orEmpty()
        if (received.size > MAX_RECEIVED_HOPS) {
            anomalies += HeaderAnomaly("RECEIVED_TOO_MANY_HOPS", EmailSecurityAnalyzer.Severity.HIGH)
        }
        if (hasInvertedTimestamps(received)) {
            anomalies += HeaderAnomaly("RECEIVED_TIMESTAMP_INVERSION", EmailSecurityAnalyzer.Severity.LOW)
        }
        listOf("date", "message-id").filter { headers[it].isNullOrEmpty() }.forEach { missing ->
            anomalies += HeaderAnomaly(
                "MISSING_${missing.uppercase(Locale.ROOT).replace('-', '_')}",
                EmailSecurityAnalyzer.Severity.LOW
            )
        }
        val usesMime = headers.keys.any { it == "content-type" || it == "content-transfer-encoding" || it == "content-disposition" }
        if (usesMime && headers["mime-version"].isNullOrEmpty()) {
            anomalies += HeaderAnomaly("MISSING_MIME_VERSION", EmailSecurityAnalyzer.Severity.LOW)
        }
        headers["from"]?.firstOrNull()?.let { from ->
            val display = FROM_DISPLAY.find(from)?.groupValues?.get(1)?.trim()?.lowercase(Locale.ROOT)
            if (display in GENERIC_FORGED_NAMES) {
                anomalies += HeaderAnomaly("SUSPICIOUS_FROM_DISPLAY_NAME", EmailSecurityAnalyzer.Severity.LOW)
            }
        }
        return EmailHeaderReport(received, received.size, anomalies)
    }

    fun parseHeaders(raw: String): Map<String, List<String>>? {
        val unfolded = mutableListOf<String>()
        for (line in raw.lineSequence()) {
            if (line.length > MAX_LINE_LENGTH) return null
            if (line.startsWith(' ') || line.startsWith('\t')) {
                if (unfolded.isEmpty()) return null
                unfolded[unfolded.lastIndex] = unfolded.last() + " " + line.trim()
            } else if (line.isNotBlank()) unfolded += line
            if (unfolded.size > MAX_HEADERS) return null
        }
        val parsed = linkedMapOf<String, MutableList<String>>()
        for (line in unfolded) {
            val separator = line.indexOf(':')
            if (separator <= 0) return null
            val name = line.substring(0, separator).trim().lowercase(Locale.ROOT)
            if (!HEADER_NAME.matches(name)) return null
            parsed.getOrPut(name) { mutableListOf() } += line.substring(separator + 1).trim()
        }
        return parsed
    }

    private fun hasInvertedTimestamps(received: List<String>): Boolean {
        val timestamps = received.mapNotNull { parseReceivedTimestamp(it) }
        if (timestamps.size < 2) return false
        return timestamps.zipWithNext().any { (newer, older) -> newer < older }
    }

    private fun parseReceivedTimestamp(value: String): Long? {
        val raw = value.substringAfterLast(';', missingDelimiterValue = "").trim()
            .replace(Regex("\\s+\\([^)]*\\)"), "")
        if (raw.isBlank()) return null
        return DATE_FORMATS.firstNotNullOfOrNull { pattern ->
            val format = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val position = ParsePosition(0)
            val parsed = format.parse(raw, position)
            if (parsed != null && position.index == raw.length) parsed.time else null
        }
    }

    const val MAX_RECEIVED_HOPS = 20
    private val DATE_FORMATS = listOf(
        "EEE, d MMM yyyy HH:mm:ss Z", "d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm Z", "d MMM yyyy HH:mm Z"
    )
}

data class EmailHeaderReport(
    val receivedHeaders: List<String> = emptyList(),
    val hopCount: Int = 0,
    val anomalies: List<HeaderAnomaly> = emptyList()
)

data class HeaderAnomaly(
    val code: String,
    val severity: EmailSecurityAnalyzer.Severity
)
