package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SentinelVpnRuntimeStateRegistryTest {
    @Before
    fun clearRegistry() {
        SentinelVpnRuntimeStateRegistry.clearForTests()
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
}
