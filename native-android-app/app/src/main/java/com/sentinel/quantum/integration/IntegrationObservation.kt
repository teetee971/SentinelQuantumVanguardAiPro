package com.sentinel.quantum.integration

data class IntegrationObservation(
    val capabilityId: IntegrationCapabilityId,
    val status: IntegrationStatus,
    val observedAtMs: Long
) {
    init {
        require(observedAtMs >= 0L) { "observedAtMs must be non-negative" }
    }
}
