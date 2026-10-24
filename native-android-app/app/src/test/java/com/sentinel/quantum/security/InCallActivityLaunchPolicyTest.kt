package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InCallActivityLaunchPolicyTest {
    @Test
    fun onlyTelecomForegroundRequestMayStartTheActivity() {
        assertTrue(
            InCallActivityLaunchPolicy.shouldRequest(
                InCallActivityLaunchPolicy.Trigger.TELECOM_BRING_TO_FOREGROUND,
                hasActiveCall = true
            )
        )
        assertFalse(
            InCallActivityLaunchPolicy.shouldRequest(
                InCallActivityLaunchPolicy.Trigger.CALL_ADDED,
                hasActiveCall = true
            )
        )
        assertFalse(
            InCallActivityLaunchPolicy.shouldRequest(
                InCallActivityLaunchPolicy.Trigger.INCOMING_NOTIFICATION_FALLBACK,
                hasActiveCall = true
            )
        )
    }

    @Test
    fun noActiveCallMeansNoLaunchRequest() {
        assertFalse(
            InCallActivityLaunchPolicy.shouldRequest(
                InCallActivityLaunchPolicy.Trigger.TELECOM_BRING_TO_FOREGROUND,
                hasActiveCall = false
            )
        )
    }
}
