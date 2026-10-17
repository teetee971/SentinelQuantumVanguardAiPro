package com.sentinel.quantum.security

import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationDiagnosticReason
import com.sentinel.quantum.integration.IntegrationState
import com.sentinel.quantum.integration.IntegrationStatus
import com.sentinel.quantum.integration.IntegrationTruthRuntime

/**
 * Vendor-neutral runtime boundary between platform wearable adapters and Sentinel product truth.
 * Platform visibility never authenticates Sentinel. Only the security composition layer may call
 * the internal authenticated entry point; explicit revocation overwrites prior connected evidence.
 */
class WearableRuntimeCoordinator {
    fun observePlatform(
        observation: WearableAdapterObservation,
        nowMs: Long = System.currentTimeMillis()
    ): WearableAdapterTruth = observeAuthenticatedChannel(
        observation = observation,
        channelProof = null,
        negotiatedCapabilities = emptySet(),
        nowMs = nowMs
    )

    internal fun observeAuthenticatedChannel(
        observation: WearableAdapterObservation,
        channelProof: WearableChannelProof?,
        negotiatedCapabilities: Set<WearableCapability>,
        nowMs: Long = System.currentTimeMillis()
    ): WearableAdapterTruth {
        val truth = WearableAdapterPolicy.evaluate(
            observation = observation,
            channelProof = channelProof,
            negotiatedCapabilities = negotiatedCapabilities,
            nowMs = nowMs
        )

        if (truth.sentinelReachable) {
            IntegrationTruthRuntime.publishConnected(
                capabilityId = IntegrationCapabilityId.WEARABLE_RUNTIME,
                observedAtMs = nowMs
            )
        } else {
            IntegrationTruthRuntime.publish(
                capabilityId = IntegrationCapabilityId.WEARABLE_RUNTIME,
                status = IntegrationStatus(
                    state = IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
                    diagnosticReason = if (truth.adapterAvailable) {
                        IntegrationDiagnosticReason.IDENTITY_BINDING_MISSING
                    } else {
                        IntegrationDiagnosticReason.TRANSPORT_NOT_IMPLEMENTED
                    }
                ),
                observedAtMs = nowMs
            )
        }
        return truth
    }

    internal fun revokeAuthenticatedChannel(nowMs: Long = System.currentTimeMillis()): Boolean =
        IntegrationTruthRuntime.publish(
            capabilityId = IntegrationCapabilityId.WEARABLE_RUNTIME,
            status = IntegrationStatus(
                state = IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
                diagnosticReason = IntegrationDiagnosticReason.IDENTITY_BINDING_MISSING
            ),
            observedAtMs = nowMs
        )
}
