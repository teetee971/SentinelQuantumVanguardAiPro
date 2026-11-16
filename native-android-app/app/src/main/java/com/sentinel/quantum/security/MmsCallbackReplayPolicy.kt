package com.sentinel.quantum.security

/**
 * Pure bounded replay policy for outgoing MMS telephony callbacks.
 *
 * A callback token may create at most one business transition during the tombstone TTL. Future
 * timestamps fail closed instead of being silently discarded. Expired tombstones may be pruned.
 */
object MmsCallbackReplayPolicy {
    const val TTL_MS: Long = 48L * 60L * 60L * 1000L
    const val MAX_ENTRIES: Int = 256

    fun shouldAccept(existingAtMs: Long?, nowMs: Long): Boolean {
        if (nowMs < 0L) return false
        if (existingAtMs == null) return true
        if (existingAtMs < 0L || existingAtMs > nowMs) return false
        return nowMs - existingAtMs > TTL_MS
    }

    fun keysToPrune(entries: Map<String, Long>, nowMs: Long): Set<String> {
        if (nowMs < 0L) return emptySet()

        val expired = entries
            .filterValues { timestamp ->
                timestamp >= 0L && timestamp <= nowMs && nowMs - timestamp > TTL_MS
            }
            .keys
            .toMutableSet()

        val retained = entries
            .filterKeys { it !in expired }
            .toList()
            .sortedBy { (_, timestamp) -> timestamp }

        val overflow = (retained.size - (MAX_ENTRIES - 1)).coerceAtLeast(0)
        retained.take(overflow).forEach { (key, _) -> expired += key }
        return expired
    }
}
