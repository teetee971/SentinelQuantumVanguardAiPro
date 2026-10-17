package com.sentinel.quantum.security

import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationDiagnosticReason
import com.sentinel.quantum.integration.IntegrationState
import com.sentinel.quantum.integration.IntegrationStatus
import com.sentinel.quantum.integration.IntegrationTruthRuntime

/**
 * Vendor-neutral runtime boundary between platform wearable adapters and Sentinel product truth.
 *
 * A platform association is not authentication. CONNECTED is published only while a concrete
 * adapter is available and a fresh Sentinel channel proof is active. Any missing/stale proof
 * immediately replaces the prior observation with a fail-closed identity-binding diagnostic.
 *
 * The caller that supplies [channelProof] is responsible for obtaining it only after the existing
 * wearable cryptographic handshake/session policy has accepted the peer. This coordinator does not
 * manufacture or infer cryptographic trust from Bluetooth/Wear OS platform state.
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

    fun observeAuthenticatedChannel(
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
}
