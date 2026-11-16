package com.sentinel.quantum.security

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelVpnModeArbiterTest {
    @After
    fun cleanup() {
        SentinelVpnModeArbiter.resetForTests()
    }

    @Test
    fun sameModeCanReacquireButOtherModeIsRejected() {
        assertTrue(SentinelVpnModeArbiter.acquire(SentinelVpnModeArbiter.Mode.INTERNET_VPN))
        assertTrue(SentinelVpnModeArbiter.acquire(SentinelVpnModeArbiter.Mode.INTERNET_VPN))
        assertFalse(SentinelVpnModeArbiter.acquire(SentinelVpnModeArbiter.Mode.PRIVATE_MESH))
        assertEquals(
            SentinelVpnModeArbiter.Mode.INTERNET_VPN,
            SentinelVpnModeArbiter.current()
        )
    }

    @Test
    fun releaseAllowsTheOtherMode() {
        assertTrue(SentinelVpnModeArbiter.acquire(SentinelVpnModeArbiter.Mode.PRIVATE_MESH))
        SentinelVpnModeArbiter.release(SentinelVpnModeArbiter.Mode.PRIVATE_MESH)
        assertNull(SentinelVpnModeArbiter.current())
        assertTrue(SentinelVpnModeArbiter.acquire(SentinelVpnModeArbiter.Mode.INTERNET_VPN))
    }
}
