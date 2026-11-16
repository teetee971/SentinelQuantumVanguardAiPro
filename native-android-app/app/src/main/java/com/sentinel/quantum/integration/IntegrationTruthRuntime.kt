package com.sentinel.quantum.integration

/**
 * Process-local bridge between concrete runtime observations and the fail-closed product truth
 * registry. A capability is CONNECTED only while there is fresh executable evidence for it.
 * Missing or stale evidence always falls back to RUNTIME_NOT_OBSERVED.
 */
object IntegrationTruthRuntime {
    private const val OBSERVATION_TTL_MS = 30_000L
    private val lock = Any()

    @Volatile
    private var registry: IntegrationRegistry = newRegistry()

    fun publishConnected(
        capabilityId: IntegrationCapabilityId,
        observedAtMs: Long = System.currentTimeMillis()
    ): Boolean = publish(
        capabilityId = capabilityId,
        status = IntegrationStatus(IntegrationState.CONNECTED),
        observedAtMs = observedAtMs
    )

    fun publish(
        capabilityId: IntegrationCapabilityId,
        status: IntegrationStatus,
        observedAtMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (observedAtMs < 0L) return false
        return registry.publish(
            IntegrationObservation(
                capabilityId = capabilityId,
                status = status,
                observedAtMs = observedAtMs
            )
        )
    }

    fun snapshot(nowMs: Long = System.currentTimeMillis()): IntegrationSnapshot =
        registry.snapshot(nowMs)

    internal fun clearForTests() {
        synchronized(lock) {
            registry = newRegistry()
        }
    }

    private fun newRegistry(): IntegrationRegistry = IntegrationRegistry(
        baselineStatuses = emptyMap(),
        observationTtlMs = OBSERVATION_TTL_MS
    )
}
