package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAgentHeartbeatPolicyTest {

    @Test
    fun missingHeartbeatIsUnknown() {
        val result = LocalAgentHeartbeatPolicy.assess(null, observedAtMs = 100_000L)

        assertEquals(LocalAgentHealth.UNKNOWN, result.health)
        assertNull(result.ageMs)
    }

    @Test
    fun recentHeartbeatIsHealthy() {
        val result = LocalAgentHeartbeatPolicy.assess(
            lastHeartbeatAtMs = 80_000L,
            observedAtMs = 100_000L,
            expectedIntervalMs = 30_000L
        )

        assertEquals(LocalAgentHealth.HEALTHY, result.health)
        assertEquals(20_000L, result.ageMs)
    }

    @Test
    fun missedHeartbeatsBecomeDegradedThenOffline() {
        val degraded = LocalAgentHeartbeatPolicy.assess(
            lastHeartbeatAtMs = 0L,
            observedAtMs = 90_000L,
            expectedIntervalMs = 30_000L
        )
        val offline = LocalAgentHeartbeatPolicy.assess(
            lastHeartbeatAtMs = 0L,
            observedAtMs = 300_000L,
            expectedIntervalMs = 30_000L
        )

        assertEquals(LocalAgentHealth.DEGRADED, degraded.health)
        assertEquals(LocalAgentHealth.OFFLINE, offline.health)
    }

    @Test
    fun futureTimestampCannotProduceNegativeAge() {
        val result = LocalAgentHeartbeatPolicy.assess(
            lastHeartbeatAtMs = 200_000L,
            observedAtMs = 100_000L
        )

        assertEquals(LocalAgentHealth.HEALTHY, result.health)
        assertEquals(0L, result.ageMs)
    }
}
