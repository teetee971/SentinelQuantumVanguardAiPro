package com.sentinel.quantum.security

import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationState
import com.sentinel.quantum.integration.IntegrationTruthRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SentinelVpnRuntimeStateRegistryTest {
    @Before
    fun clearRegistry() {
        SentinelVpnRuntimeStateRegistry.clearForTests()
        IntegrationTruthRuntime.clearForTests()
    }

    @Test
    fun expiredProtectedObservationIsNotReturnedAsCurrent() {
        SentinelVpnRuntimeStateRegistry.publish(
            SentinelVpnController.RuntimeState.PROTECTED,
            observedAtEpochMillis = 1_000L
        )

        assertNull(
            SentinelVpnRuntimeStateRegistry.current(
                nowEpochMillis = 31_001L,
                maxAgeMs = 30_000L
            )
        )
    }

    @Test
    fun freshObservationIsSharedAcrossConsumers() {
        SentinelVpnRuntimeStateRegistry.publish(
            SentinelVpnController.RuntimeState.CONNECTING,
            observedAtEpochMillis = 1_000L
        )

        assertEquals(
            SentinelVpnController.RuntimeState.CONNECTING,
            SentinelVpnRuntimeStateRegistry.current(
                nowEpochMillis = 1_500L,
                maxAgeMs = 30_000L
            )?.state
        )
    }

    @Test
    fun protectedStatePublishesFreshConnectedTruth() {
        SentinelVpnRuntimeStateRegistry.publish(
            SentinelVpnController.RuntimeState.PROTECTED,
            observedAtEpochMillis = 2_000L
        )

        assertEquals(
            IntegrationState.CONNECTED,
            IntegrationTruthRuntime.snapshot(nowMs = 2_001L)
                .status(IntegrationCapabilityId.VPN_RUNTIME).state
        )
    }

    @Test
    fun disconnectImmediatelyRevokesConnectedTruth() {
        SentinelVpnRuntimeStateRegistry.publish(
            SentinelVpnController.RuntimeState.PROTECTED,
            observedAtEpochMillis = 2_000L
        )
        SentinelVpnRuntimeStateRegistry.publish(
            SentinelVpnController.RuntimeState.DISCONNECTED,
            observedAtEpochMillis = 2_100L
        )

        assertEquals(
            IntegrationState.LOCKED_EXTERNAL_DEPENDENCY,
            IntegrationTruthRuntime.snapshot(nowMs = 2_101L)
                .status(IntegrationCapabilityId.VPN_RUNTIME).state
        )
    }
}
