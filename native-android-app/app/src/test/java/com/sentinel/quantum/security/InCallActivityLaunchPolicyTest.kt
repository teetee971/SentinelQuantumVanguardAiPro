package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InCallActivityLaunchPolicyTest {
    @Test
    fun incomingCallsRequireExplicitTelecomForegroundRequest() {
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
    fun externalOutgoingCallsMayRequestTheDefaultDialerUi() {
        assertTrue(InCallActivityLaunchPolicy.shouldRequest(
            InCallActivityLaunchPolicy.Trigger.CALL_ADDED, hasActiveCall = true, outgoingCall = true))
        assertFalse(InCallActivityLaunchPolicy.shouldRequest(
            InCallActivityLaunchPolicy.Trigger.INCOMING_NOTIFICATION_FALLBACK,
            hasActiveCall = true, outgoingCall = true))
    }

    @Test
    fun deniedPostNotificationsStillOpensIncomingCallScreen() {
        assertTrue(
            InCallActivityLaunchPolicy.shouldRequest(
                InCallActivityLaunchPolicy.Trigger.INCOMING_NOTIFICATION_FALLBACK,
                hasActiveCall = true,
                outgoingCall = false,
                notificationPermissionGranted = false
            )
        )
        assertFalse(
            InCallActivityLaunchPolicy.shouldRequest(
                InCallActivityLaunchPolicy.Trigger.INCOMING_NOTIFICATION_FALLBACK,
                hasActiveCall = false,
                outgoingCall = false,
                notificationPermissionGranted = false
            )
        )
        assertFalse(
            InCallActivityLaunchPolicy.shouldRequest(
                InCallActivityLaunchPolicy.Trigger.INCOMING_NOTIFICATION_FALLBACK,
                hasActiveCall = true,
                outgoingCall = true,
                notificationPermissionGranted = false
            )
        )
        val helper = java.io.File(
            "src/main/java/com/sentinel/quantum/security/SentinelCallNotificationHelper.kt"
        ).readText()
        val denied = helper.substringAfter("permission.POST_NOTIFICATIONS").substringBefore("val manager")
        assertTrue(denied.contains("openIncomingCallScreen"))
        assertTrue(denied.contains("INCOMING_NOTIFICATION_FALLBACK"))
        assertTrue(helper.contains("fun openIncomingCallScreen"))
        assertTrue(helper.contains("Intent(context, SentinelInCallActivity::class.java)"))
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
