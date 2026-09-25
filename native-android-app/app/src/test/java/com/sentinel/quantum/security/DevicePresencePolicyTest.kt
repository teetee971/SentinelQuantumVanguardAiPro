package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevicePresencePolicyTest {

    @Test
    fun noHistoryIsFirstSeen() {
        val result = DevicePresencePolicy.assess(null, observedAtMs = 1_000L)

        assertEquals(DevicePresenceState.FIRST_SEEN, result.state)
        assertNull(result.absenceDurationMs)
    }

    @Test
    fun recentObservationRemainsPresent() {
        val result = DevicePresencePolicy.assess(
            previousLastSeenAtMs = 1_000L,
            observedAtMs = 2_000L,
            returnAfterMs = 10_000L
        )

        assertEquals(DevicePresenceState.PRESENT, result.state)
        assertEquals(1_000L, result.absenceDurationMs)
    }

    @Test
    fun deviceReturningAfterThresholdIsExplicit() {
        val result = DevicePresencePolicy.assess(
            previousLastSeenAtMs = 1_000L,
            observedAtMs = 20_000L,
            returnAfterMs = 10_000L
        )

        assertEquals(DevicePresenceState.RETURNED_AFTER_ABSENCE, result.state)
        assertEquals(19_000L, result.absenceDurationMs)
    }

    @Test
    fun futureStoredTimestampCannotCreateNegativeAbsence() {
        val result = DevicePresencePolicy.assess(
            previousLastSeenAtMs = 50_000L,
            observedAtMs = 20_000L,
            returnAfterMs = 10_000L
        )

        assertEquals(DevicePresenceState.PRESENT, result.state)
        assertEquals(0L, result.absenceDurationMs)
    }
}
