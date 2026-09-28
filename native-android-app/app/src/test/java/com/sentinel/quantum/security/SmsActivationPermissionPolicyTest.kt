package com.sentinel.quantum.security

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SmsActivationPermissionPolicyTest {
    @Test
    fun `composer never requests inbox permissions`() {
        val snapshot = SmsActivationDiagnostics.Snapshot(
            state = SmsActivationDiagnostics.State.LOCKED,
            blockers = setOf(
                SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED,
                SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED,
                SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED,
                SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED
            ),
            activeSubscriptionIds = emptyList()
        )

        assertArrayEquals(
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE),
            SmsActivationPermissionPolicy.sendPermissions(snapshot)
        )
    }

    @Test
    fun `composer requests nothing before SMS role is held`() {
        val snapshot = SmsActivationDiagnostics.Snapshot(
            state = SmsActivationDiagnostics.State.LOCKED,
            blockers = setOf(
                SmsActivationDiagnostics.Blocker.SMS_ROLE_REQUIRED,
                SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED,
                SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED
            ),
            activeSubscriptionIds = emptyList(),
            smsRoleState = SmsActivationDiagnostics.SmsRoleState.AVAILABLE_NOT_HELD
        )

        assertArrayEquals(emptyArray<String>(), SmsActivationPermissionPolicy.sendPermissions(snapshot))
    }

    @Test
    fun `activation requests only blockers after SMS role is held`() {
        val snapshot = SmsActivationDiagnostics.Snapshot(
            state = SmsActivationDiagnostics.State.LOCKED,
            blockers = setOf(
                SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED,
                SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED
            ),
            activeSubscriptionIds = listOf(1),
            smsRoleState = SmsActivationDiagnostics.SmsRoleState.HELD
        )

        assertArrayEquals(
            arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
            SmsActivationPermissionPolicy.activationPermissions(snapshot)
        )
    }
}
