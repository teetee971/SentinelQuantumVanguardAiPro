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

    /**
     * Records the semantics used before the irreversible fingerprint was produced.
     *
     * LEGACY_UNSPECIFIED is intentionally non-runnable after the region-aware migration: older
     * builds could have silently converted any national 0… number to +33. Because the raw number
     * was never persisted, those historical fingerprints cannot be disambiguated safely.
     */
    enum class Canonicalization {
        LEGACY_UNSPECIFIED,
        REGION_AWARE_E164_V1
    }

    data class Entry(
        val fingerprint: String,
        val reason: String,
        val createdAtEpochMs: Long,
        val expiresAtEpochMs: Long?,
        val origin: Origin,
        val canonicalization: Canonicalization = Canonicalization.LEGACY_UNSPECIFIED
    ) {
        fun isActive(nowEpochMs: Long): Boolean =
            expiresAtEpochMs == null || expiresAtEpochMs > nowEpochMs

        val safeForExactMatching: Boolean
            get() = canonicalization == Canonicalization.REGION_AWARE_E164_V1
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
