package com.sentinel.quantum.security

/**
 * Prevents stale usage evidence from driving remediation indefinitely.
 */
object SentinelAppUsageFreshness {
    const val MAX_EVIDENCE_AGE_DAYS = 7L
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    enum class State { FRESH, STALE, INVALID }

    fun evaluate(observedAtEpochMillis: Long, nowEpochMillis: Long): State {
        if (observedAtEpochMillis < 0L || nowEpochMillis < 0L || observedAtEpochMillis > nowEpochMillis) {
            return State.INVALID
        }
        return if (nowEpochMillis - observedAtEpochMillis <= MAX_EVIDENCE_AGE_DAYS * DAY_MILLIS) {
            State.FRESH
        } else {
            State.STALE
        }
    }
}
