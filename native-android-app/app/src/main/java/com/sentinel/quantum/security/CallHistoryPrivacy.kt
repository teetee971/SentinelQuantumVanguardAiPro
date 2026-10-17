package com.sentinel.quantum.security

/**
 * Privacy boundary for local call-history enrichment.
 *
 * [SystemCallLogReader] exposes this bounded context without allowing raw history rows to cross the
 * enrichment boundary. Raw phone numbers and call history remain on-device; this model is never a
 * network payload.
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
