package com.sentinel.quantum.security

/**
 * Privacy boundary for future call-history enrichment.
 *
 * Sentinel may correlate local call metadata for user-facing context, but raw phone numbers
 * and call history must remain on-device unless the user explicitly enables a separate,
 * purpose-specific remote feature.
 */
object CallHistoryPrivacy {
    const val MAX_RECENT_EVENTS = 50

    data class LocalCallContext(
        val recentCalls: Int,
        val missedCalls: Int,
        val lastInteractionEpochMillis: Long?
    )

    fun boundedContext(
        recentCalls: Int,
        missedCalls: Int,
        lastInteractionEpochMillis: Long?
    ): LocalCallContext = LocalCallContext(
        recentCalls = recentCalls.coerceIn(0, MAX_RECENT_EVENTS),
        missedCalls = missedCalls.coerceIn(0, MAX_RECENT_EVENTS),
        lastInteractionEpochMillis = lastInteractionEpochMillis?.takeIf { it > 0L }
    )

    /** Deliberately false: this model is local-only and is not a network payload. */
    fun permitsRemoteTransmission(): Boolean = false
}
