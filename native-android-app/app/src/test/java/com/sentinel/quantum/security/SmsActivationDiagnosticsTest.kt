package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsActivationDiagnosticsTest {
    @Test
    fun `send remains available when only inbox permissions are missing`() {
        val snapshot = SmsActivationDiagnostics.Snapshot(
            state = SmsActivationDiagnostics.State.LOCKED,
            blockers = setOf(
                SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED,
                SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED
            ),
            activeSubscriptionIds = listOf(1)
        )

        assertTrue(snapshot.canSend)
    }

    @Test
    fun `send remains fail closed without role send permission or active sim`() {
        val roleMissing = SmsActivationDiagnostics.Snapshot(
            state = SmsActivationDiagnostics.State.LOCKED,
            blockers = setOf(SmsActivationDiagnostics.Blocker.SMS_ROLE_REQUIRED),
            activeSubscriptionIds = listOf(1)
        )
        val sendPermissionMissing = SmsActivationDiagnostics.Snapshot(
            state = SmsActivationDiagnostics.State.LOCKED,
            blockers = setOf(SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED),
            activeSubscriptionIds = listOf(1)
        )
        val phoneStateMissing = SmsActivationDiagnostics.Snapshot(
            state = SmsActivationDiagnostics.State.LIMITED,
            blockers = setOf(SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED),
            activeSubscriptionIds = emptyList()
        )
        val noActiveSim = SmsActivationDiagnostics.Snapshot(
            state = SmsActivationDiagnostics.State.LIMITED,
            blockers = setOf(SmsActivationDiagnostics.Blocker.NO_ACTIVE_SIM),
            activeSubscriptionIds = emptyList()
        )

        assertFalse(roleMissing.canSend)
        assertFalse(sendPermissionMissing.canSend)
        assertFalse(phoneStateMissing.canSend)
        assertFalse(noActiveSim.canSend)
    }
}
