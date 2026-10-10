package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelDeviceIdentityTest {
    @Test
    fun derives_a_stable_non_raw_device_identifier() {
        val first = SentinelDeviceIdentity.stableIdFor("android-id-123")
        val second = SentinelDeviceIdentity.stableIdFor("android-id-123")

        assertEquals(first, second)
        assertTrue(first?.startsWith("android-") == true)
        assertNotEquals("android-id-123", first)
    }

    @Test
    fun rejects_missing_android_identifiers() {
        assertEquals(null, SentinelDeviceIdentity.stableIdFor(null))
        assertEquals(null, SentinelDeviceIdentity.stableIdFor("  "))
    }
}
