package com.sentinel.quantum.security

/**
 * Local deterministic health policy for an optional Sentinel Local Agent.
 *
 * This class performs no network access. It classifies an already-observed heartbeat
 * timestamp so Android can distinguish healthy, degraded and offline states without
 * pretending an unavailable agent is still active.
 */
enum class LocalAgentHealth {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    OFFLINE
}

data class LocalAgentHeartbeatAssessment(
    val health: LocalAgentHealth,
    val ageMs: Long?,
    val expectedIntervalMs: Long,
    val degradedAfterMs: Long,
    val offlineAfterMs: Long
)

object LocalAgentHeartbeatPolicy {
    const val DEFAULT_EXPECTED_INTERVAL_MS = 30_000L
    const val DEFAULT_DEGRADED_MULTIPLIER = 3L
    const val DEFAULT_OFFLINE_MULTIPLIER = 10L

    fun assess(
        lastHeartbeatAtMs: Long?,
        observedAtMs: Long,
        expectedIntervalMs: Long = DEFAULT_EXPECTED_INTERVAL_MS,
        degradedMultiplier: Long = DEFAULT_DEGRADED_MULTIPLIER,
        offlineMultiplier: Long = DEFAULT_OFFLINE_MULTIPLIER
    ): LocalAgentHeartbeatAssessment {
        require(observedAtMs >= 0L) { "observedAtMs must be non-negative" }
        require(expectedIntervalMs > 0L) { "expectedIntervalMs must be positive" }
        require(degradedMultiplier > 1L) { "degradedMultiplier must be greater than one" }
        require(offlineMultiplier > degradedMultiplier) { "offlineMultiplier must exceed degradedMultiplier" }

        val degradedAfterMs = saturatingMultiply(expectedIntervalMs, degradedMultiplier)
        val offlineAfterMs = saturatingMultiply(expectedIntervalMs, offlineMultiplier)

        if (lastHeartbeatAtMs == null) {
            return LocalAgentHeartbeatAssessment(
                health = LocalAgentHealth.UNKNOWN,
                ageMs = null,
                expectedIntervalMs = expectedIntervalMs,
                degradedAfterMs = degradedAfterMs,
                offlineAfterMs = offlineAfterMs
            )
        }

        val boundedLastSeen = lastHeartbeatAtMs.coerceAtMost(observedAtMs)
        val age = observedAtMs - boundedLastSeen
        val health = when {
            age >= offlineAfterMs -> LocalAgentHealth.OFFLINE
            age >= degradedAfterMs -> LocalAgentHealth.DEGRADED
            else -> LocalAgentHealth.HEALTHY
        }

        return LocalAgentHeartbeatAssessment(
            health = health,
            ageMs = age,
            expectedIntervalMs = expectedIntervalMs,
            degradedAfterMs = degradedAfterMs,
            offlineAfterMs = offlineAfterMs
        )
    }

    private fun saturatingMultiply(value: Long, multiplier: Long): Long =
        if (value > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else value * multiplier
}
