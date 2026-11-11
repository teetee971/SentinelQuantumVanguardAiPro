package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiConnectPlanTest {
    @Test
    fun onlyTheUsersOwnKeyCanBeRequested() {
        assertTrue(WifiConnectPlan.needsKey(WifiSecurityType.WPA2))
        assertFalse(WifiConnectPlan.needsKey(WifiSecurityType.OPEN))
        assertFalse(WifiConnectPlan.canRequest(WifiSecurityType.WEP))
        assertTrue(WifiConnectPlan.usableKey("maison-123"))
        assertFalse(WifiConnectPlan.usableKey("court"))
    }
}
