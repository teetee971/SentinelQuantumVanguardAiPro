package com.sentinel.quantum.security

/**
 * Pure policy for user-created exact-number blocks. The store persists metadata by keyed
 * fingerprint; the raw phone number is deliberately absent from this model.
 */
object CallBlockMetadata {
    enum class Duration(val durationMs: Long?) {
        HOURS_24(24L * 60L * 60L * 1000L),
        DAYS_7(7L * 24L * 60L * 60L * 1000L),
        DAYS_30(30L * 24L * 60L * 60L * 1000L),
        PERMANENT(null)
    }

    enum class Origin { MANUAL, AUTOMATED }

    data class Entry(
        val fingerprint: String,
        val reason: String,
        val createdAtEpochMs: Long,
        val expiresAtEpochMs: Long?,
        val origin: Origin
    ) {
        fun isActive(nowEpochMs: Long): Boolean =
            expiresAtEpochMs == null || expiresAtEpochMs > nowEpochMs
    }

    fun expiresAt(createdAtEpochMs: Long, duration: Duration): Long? =
        duration.durationMs?.let { delta ->
            if (createdAtEpochMs > Long.MAX_VALUE - delta) Long.MAX_VALUE
            else createdAtEpochMs + delta
        }

    fun sanitizeReason(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_REASON_CHARS)

    const val MAX_REASON_CHARS = 120
}
