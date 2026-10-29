package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class IncomingCallAlertPolicyTest {
    @Test fun firstIncomingCallAlertsButItsUpdatesDoNot() {
        val policy = IncomingCallAlertPolicy()
        assertTrue(policy.shouldAlert(true, "a"))
        policy.posted(true, "a")
        assertFalse(policy.shouldAlert(true, "a"))
    }
    @Test fun waitingCallAlertsOverAnOngoingNotification() {
        val policy = IncomingCallAlertPolicy()
        policy.posted(true, "a")
        policy.posted(false, "a")
        assertTrue(policy.shouldAlert(true, "b"))
        policy.posted(true, "b")
        assertFalse(policy.shouldAlert(true, "b"))
        assertTrue(policy.shouldAlert(true, "c"))
    }
    @Test fun unpublishedIncomingCallMayRetryAndOngoingDoesNotAlert() {
        val policy = IncomingCallAlertPolicy()
        assertTrue(policy.shouldAlert(true, "a"))
        assertTrue(policy.shouldAlert(true, "a"))
        assertFalse(policy.shouldAlert(false, "a"))
    }
}
