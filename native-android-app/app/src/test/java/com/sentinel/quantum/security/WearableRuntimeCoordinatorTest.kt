package com.sentinel.quantum.security

import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationDiagnosticReason
import com.sentinel.quantum.integration.IntegrationState
import com.sentinel.quantum.integration.IntegrationTruthRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableRuntimeCoordinatorTest {
    @After
    fun clearRuntimeTruth() {
        IntegrationTruthRuntime.clearForTests()
    }

    private fun wearOsObservation() = WearableAdapterObservation(
        stableIdHint = "wearos-test-node",
        displayName = "Test watch",
        transport = WearableTransport.WEAR_OS,
        platformLinkState = WearablePlatformLinkState.COMPANION_ASSOCIATED,
        availability = WearableAdapterAvailability.AVAILABLE,
        observedCapabilities = setOf(WearableCapability.CONNECTION_STATE)
    )

    @Test
    fun platformLinkAloneNeverClaimsSentinelConnected() {
        val coordinator = WearableRuntimeCoordinator()
        val truth = coordinator.observePlatform(wearOsObservation(), nowMs = 10_000L)

        assertFalse(truth.sentinelReachable)
        val status = IntegrationTruthRuntime.snapshot(10_000L)
            .status(IntegrationCapabilityId.WEARABLE_RUNTIME)
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, status.state)
        assertEquals(IntegrationDiagnosticReason.IDENTITY_BINDING_MISSING, status.diagnosticReason)
    }

    @Test
    fun freshAuthenticatedChannelCanPublishConnected() {
        val coordinator = WearableRuntimeCoordinator()
        val truth = coordinator.observeAuthenticatedChannel(
            observation = wearOsObservation(),
            channelProof = WearableChannelProof(
                state = WearableChannelState.ACTIVE,
                lastProofAtMs = 10_000L,
                ttlMs = 30_000L
            ),
            negotiatedCapabilities = setOf(WearableCapability.SENTINEL_ALERTS),
            nowMs = 10_000L
        )

        assertTrue(truth.sentinelReachable)
        assertEquals(
            IntegrationState.CONNECTED,
            IntegrationTruthRuntime.snapshot(10_000L)
                .status(IntegrationCapabilityId.WEARABLE_RUNTIME)
                .state
        )
    }

    @Test
    fun staleAuthenticatedChannelRevokesConnectedTruth() {
        val coordinator = WearableRuntimeCoordinator()
        coordinator.observeAuthenticatedChannel(
            observation = wearOsObservation(),
            channelProof = WearableChannelProof(
                state = WearableChannelState.ACTIVE,
                lastProofAtMs = 10_000L,
                ttlMs = 30_000L
            ),
            negotiatedCapabilities = setOf(WearableCapability.SENTINEL_ALERTS),
            nowMs = 10_000L
        )

        val truth = coordinator.observeAuthenticatedChannel(
            observation = wearOsObservation(),
            channelProof = WearableChannelProof(
                state = WearableChannelState.ACTIVE,
                lastProofAtMs = 10_000L,
                ttlMs = 30_000L
            ),
            negotiatedCapabilities = setOf(WearableCapability.SENTINEL_ALERTS),
            nowMs = 40_000L
        )

        assertFalse(truth.sentinelReachable)
        val status = IntegrationTruthRuntime.snapshot(40_000L)
            .status(IntegrationCapabilityId.WEARABLE_RUNTIME)
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, status.state)
        assertEquals(IntegrationDiagnosticReason.IDENTITY_BINDING_MISSING, status.diagnosticReason)
    }
}
