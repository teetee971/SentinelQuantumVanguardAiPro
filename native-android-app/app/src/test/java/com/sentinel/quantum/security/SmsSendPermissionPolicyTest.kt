package com.sentinel.quantum.security

import android.Manifest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SmsSendPermissionPolicyTest {
    @Test
    fun `send path requests only send and phone state permissions`() {
        val blockers = setOf(
            SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED,
            SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED,
            SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED,
            SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED,
        )

        val permissions = SmsSendPermissionPolicy.permissionsFor(blockers)

        assertArrayEquals(
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE),
            permissions,
        )
        assertFalse(permissions.contains(Manifest.permission.READ_SMS))
        assertFalse(permissions.contains(Manifest.permission.RECEIVE_SMS))
    }

    @Test
    fun `send path returns no unrelated permissions when only inbox permissions are missing`() {
        val permissions = SmsSendPermissionPolicy.permissionsFor(
            setOf(
                SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED,
                SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED,
            ),
        )

        assertArrayEquals(emptyArray<String>(), permissions)
    }

    @Test
    fun `send path requests only the actually missing send permission`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.SEND_SMS),
            SmsSendPermissionPolicy.permissionsFor(
                setOf(SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED),
            ),
        )
    }
}
