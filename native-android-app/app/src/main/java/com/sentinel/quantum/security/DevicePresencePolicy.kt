package com.sentinel.quantum.security

/**
 * Deterministic local policy for "new device" and "device returned" signals.
 *
 * Persistence is intentionally separate. Callers may store only a device-bound
 * fingerprint plus timestamps/counters; raw BSSID/MAC values do not need to be retained.
 */
enum class DevicePresenceState {
    FIRST_SEEN,
    PRESENT,
    RETURNED_AFTER_ABSENCE
}

data class DevicePresenceAssessment(
    val state: DevicePresenceState,
    val previousLastSeenAtMs: Long?,
    val observedAtMs: Long,
    val absenceDurationMs: Long?
)

object DevicePresencePolicy {
    const val DEFAULT_RETURN_AFTER_MS = 6L * 60L * 60L * 1000L

    fun assess(
        previousLastSeenAtMs: Long?,
        observedAtMs: Long,
        returnAfterMs: Long = DEFAULT_RETURN_AFTER_MS
    ): DevicePresenceAssessment {
        require(observedAtMs >= 0L) { "observedAtMs must be non-negative" }
        require(returnAfterMs > 0L) { "returnAfterMs must be positive" }

        if (previousLastSeenAtMs == null) {
            return DevicePresenceAssessment(
                state = DevicePresenceState.FIRST_SEEN,
                previousLastSeenAtMs = null,
                observedAtMs = observedAtMs,
                absenceDurationMs = null
            )
        }

        val boundedPrevious = previousLastSeenAtMs.coerceAtMost(observedAtMs)
        val absence = observedAtMs - boundedPrevious
        val state = if (absence >= returnAfterMs) {
            DevicePresenceState.RETURNED_AFTER_ABSENCE
        } else {
            DevicePresenceState.PRESENT
        }

        return DevicePresenceAssessment(
            state = state,
            previousLastSeenAtMs = previousLastSeenAtMs,
            observedAtMs = observedAtMs,
            absenceDurationMs = absence
        )
    }
}
