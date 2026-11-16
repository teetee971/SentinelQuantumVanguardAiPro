package com.sentinel.quantum.integration

class IntegrationRegistry(
    baselineStatuses: Map<IntegrationCapabilityId, IntegrationStatus>,
    private val observationTtlMs: Long = DEFAULT_OBSERVATION_TTL_MS
) {
    private val baseline = baselineStatuses.toMap()
    private val observations = mutableMapOf<IntegrationCapabilityId, IntegrationObservation>()

    init {
        require(observationTtlMs >= 0L) { "observationTtlMs must be non-negative" }
    }

    @Synchronized
    fun publish(observation: IntegrationObservation): Boolean {
        val current = observations[observation.capabilityId]
        if (current == null) {
            observations[observation.capabilityId] = observation
            return true
        }
        if (observation.observedAtMs < current.observedAtMs) return false
        if (observation.observedAtMs == current.observedAtMs) {
            if (observation == current) return true
            val currentConnected = current.status.state == IntegrationState.CONNECTED
            val incomingConnected = observation.status.state == IntegrationState.CONNECTED
            if (currentConnected && !incomingConnected) {
                observations[observation.capabilityId] = observation
                return true
            }
            return false
        }
        observations[observation.capabilityId] = observation
        return true
    }

    @Synchronized
    fun snapshot(nowMs: Long): IntegrationSnapshot {
        val statuses = IntegrationCapabilityId.entries.associateWith { capabilityId ->
            val observed = observations[capabilityId]
            if (isFresh(observed, nowMs)) {
                observed!!.status
            } else {
                baseline[capabilityId] ?: IntegrationSnapshot.FAIL_CLOSED_STATUS
            }
        }
        return IntegrationSnapshot(nowMs, statuses)
    }

    private fun isFresh(observation: IntegrationObservation?, nowMs: Long): Boolean {
        if (observation == null || nowMs < 0L) return false
        if (observation.observedAtMs > nowMs) return false
        return nowMs - observation.observedAtMs <= observationTtlMs
    }

    private companion object {
        const val DEFAULT_OBSERVATION_TTL_MS = 30_000L
    }
}
