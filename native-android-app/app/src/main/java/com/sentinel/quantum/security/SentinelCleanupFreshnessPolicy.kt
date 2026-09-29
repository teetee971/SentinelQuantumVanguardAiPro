package com.sentinel.quantum.security

/**
 * Fail-closed pre-delete identity check for a previously discovered cleanup candidate.
 * A candidate is fresh only when every observation captured at discovery still matches.
 */
object SentinelCleanupFreshnessPolicy {
    data class Observation(
        val bytes: Long?,
        val lastModifiedEpochMillis: Long?
    )

    fun isFresh(
        discovered: Observation,
        current: Observation
    ): Boolean {
        if (discovered.bytes != null && current.bytes != discovered.bytes) return false
        if (
            discovered.lastModifiedEpochMillis != null &&
            current.lastModifiedEpochMillis != discovered.lastModifiedEpochMillis
        ) return false
        return true
    }
}
