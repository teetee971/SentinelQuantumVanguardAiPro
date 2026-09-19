package com.sentinel.quantum.security

/**
 * Sanitized, bounded Phone Core decision record.
 * Never stores raw SMS content, contacts, OTPs, or arbitrary metadata.
 */
object PhoneDecisionJournal {
    enum class Action { ALLOW, SILENCE, BLOCK, WARN }

    data class Entry(
        val timestampMs: Long,
        val action: Action,
        val ruleCode: String,
        val confidence: SentinelConfidence,
        val provenance: String,
        val localOnly: Boolean
    )

    fun sanitize(entry: Entry, nowMs: Long): Entry? {
        if (entry.timestampMs !in 0..nowMs) return null
        val rule = token(entry.ruleCode, MAX_RULE)
        val provenance = token(entry.provenance, MAX_PROVENANCE)
        if (rule.isBlank() || provenance.isBlank()) return null
        return entry.copy(ruleCode = rule, provenance = provenance)
    }

    fun bounded(entries: List<Entry>, nowMs: Long): List<Entry> =
        entries.asSequence()
            .mapNotNull { sanitize(it, nowMs) }
            .filter { nowMs - it.timestampMs <= RETENTION_MS }
            .sortedByDescending { it.timestampMs }
            .take(MAX_ENTRIES)
            .toList()

    private fun token(value: String, max: Int): String =
        value.filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == ':' }.take(max)

    private const val MAX_RULE = 64
    private const val MAX_PROVENANCE = 96
    private const val MAX_ENTRIES = 200
    private const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
}
