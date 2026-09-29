package com.sentinel.quantum.security

/**
 * Fail-closed pre-delete identity check for a previously discovered cleanup candidate.
 * Cleanup is permitted only when both size and last-modified evidence were captured
 * at discovery and remain exactly unchanged immediately before deletion.
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
        val discoveredBytes = discovered.bytes ?: return false
        val discoveredModified = discovered.lastModifiedEpochMillis ?: return false
        val currentBytes = current.bytes ?: return false
        val currentModified = current.lastModifiedEpochMillis ?: return false

        return discoveredBytes == currentBytes &&
            discoveredModified == currentModified
    }
}
