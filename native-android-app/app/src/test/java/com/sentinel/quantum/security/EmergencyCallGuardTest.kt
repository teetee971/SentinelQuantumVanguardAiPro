package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyCallGuardTest {
    @Test fun platformConfirmedEmergencyAlwaysAllows() {
        EmergencyCallGuard.RequestedAction.values().forEach { requested ->
            val result = EmergencyCallGuard.apply(true, requested)
            assertEquals(EmergencyCallGuard.RequestedAction.ALLOW, result.action)
            assertEquals("PLATFORM_CONFIRMED_EMERGENCY", result.reason)
        }
    }

    @Test fun platformConfirmedEmergencyBypassesExplicitSimSelection() {
        assertFalse(EmergencyCallGuard.requiresExplicitPhoneAccountSelection(true))
        assertTrue(EmergencyCallGuard.requiresExplicitPhoneAccountSelection(false))
    }

    @Test fun ordinaryCallKeepsRequestedPolicy() {
        val result = EmergencyCallGuard.apply(false, EmergencyCallGuard.RequestedAction.BLOCK)
        assertEquals(EmergencyCallGuard.RequestedAction.BLOCK, result.action)
        assertFalse(result.bypassedOrdinaryProtection)
    }

    @Test fun reportsBypassWhenProtectionWouldInterfere() {
        assertTrue(EmergencyCallGuard.apply(true, EmergencyCallGuard.RequestedAction.CONFIRM_CALLBACK).bypassedOrdinaryProtection)
    }
}
