package com.sentinel.quantum.security

import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationDiagnosticReason
import com.sentinel.quantum.integration.IntegrationState
import com.sentinel.quantum.integration.IntegrationStatus
import com.sentinel.quantum.integration.IntegrationTruthRuntime

/**
 * Process-local observation bridge for the VPN runtime.
 *
 * This is not durable trust and it is not a gateway catalog. It only lets surfaces in the same
 * process explain the most recent controller transition. Expired observations are discarded so
 * a past PROTECTED state cannot survive as a current protection claim.
 */
object SentinelVpnRuntimeStateRegistry {
    data class Observation(
        val state: SentinelVpnController.RuntimeState,
        val observedAtEpochMillis: Long
    )

    @Volatile
    private var observation: Observation? = null

    fun publish(
        state: SentinelVpnController.RuntimeState,
        observedAtEpochMillis: Long = System.currentTimeMillis()
    ) {
        if (observedAtEpochMillis < 0L) return
        observation = Observation(state, observedAtEpochMillis)
        publishIntegrationTruth(state, observedAtEpochMillis)
    }

    fun current(
        nowEpochMillis: Long = System.currentTimeMillis(),
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS
    ): Observation? {
        if (nowEpochMillis < 0L || maxAgeMs < 0L) return null
        val value = observation ?: return null
        if (value.observedAtEpochMillis > nowEpochMillis) return null
        if (nowEpochMillis - value.observedAtEpochMillis > maxAgeMs) return null
        return value
    }

    internal fun clearForTests() {
        observation = null
    }

    private fun publishIntegrationTruth(
        state: SentinelVpnController.RuntimeState,
        observedAtEpochMillis: Long
    ) {
        val status = when (state) {
            SentinelVpnController.RuntimeState.PROTECTED -> IntegrationStatus(
                IntegrationState.CONNECTED
            )
            SentinelVpnController.RuntimeState.READY_NO_GATEWAY -> IntegrationStatus(
                IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
                IntegrationDiagnosticReason.GATEWAY_NOT_AVAILABLE
            )
            SentinelVpnController.RuntimeState.CONSENT_REQUIRED -> IntegrationStatus(
                IntegrationState.PHYSICAL_REQUIRED,
                IntegrationDiagnosticReason.PHYSICAL_VALIDATION_REQUIRED
            )
            SentinelVpnController.RuntimeState.CONNECTING,
            SentinelVpnController.RuntimeState.DEGRADED,
            SentinelVpnController.RuntimeState.FAILED,
            SentinelVpnController.RuntimeState.DISCONNECTED -> IntegrationStatus(
                IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
                IntegrationDiagnosticReason.RUNTIME_NOT_OBSERVED
            )
        }
        IntegrationTruthRuntime.publish(
            capabilityId = IntegrationCapabilityId.VPN_RUNTIME,
            status = status,
            observedAtMs = observedAtEpochMillis
        )
    }

    private const val DEFAULT_MAX_AGE_MS = 30_000L
}
