package com.sentinel.quantum.integration

data class IntegrationSnapshot internal constructor(
    val capturedAtMs: Long,
    val statuses: Map<IntegrationCapabilityId, IntegrationStatus>
) {
    fun status(capabilityId: IntegrationCapabilityId): IntegrationStatus =
        statuses[capabilityId] ?: FAIL_CLOSED_STATUS

    companion object {
        val FAIL_CLOSED_STATUS = IntegrationStatus(
            IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
            IntegrationDiagnosticReason.RUNTIME_NOT_OBSERVED
        )
    }
}
