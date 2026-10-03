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

    @Test
    fun `activation path is fail closed until sms role is held`() {
        val blockers = setOf(
            SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED,
            SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED,
            SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED,
            SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED,
        )

        assertArrayEquals(
            emptyArray<String>(),
            SmsActivationPermissionPolicy.permissionsFor(
                SmsActivationDiagnostics.SmsRoleState.AVAILABLE_NOT_HELD,
                blockers,
            ),
        )
        assertArrayEquals(
            emptyArray<String>(),
            SmsActivationPermissionPolicy.permissionsFor(
                SmsActivationDiagnostics.SmsRoleState.UNAVAILABLE,
                blockers,
            ),
        )
    }

    @Test
    fun `activation path completes send phase before exposing inbox permissions`() {
        val permissions = SmsActivationPermissionPolicy.permissionsFor(
            SmsActivationDiagnostics.SmsRoleState.HELD,
            setOf(
                SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED,
                SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED,
                SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED,
                SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED,
            ),
        )

        assertArrayEquals(
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE),
            permissions,
        )
        assertFalse(permissions.contains(Manifest.permission.READ_SMS))
        assertFalse(permissions.contains(Manifest.permission.RECEIVE_SMS))
    }

    @Test
    fun `activation path exposes inbox phase after send phase is complete`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
            SmsActivationPermissionPolicy.permissionsFor(
                SmsActivationDiagnostics.SmsRoleState.HELD,
                setOf(
                    SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED,
                    SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED,
                ),
            ),
        )
    }

    @Test
    fun `activation path is empty when all runtime permissions are satisfied`() {
        assertArrayEquals(
            emptyArray<String>(),
            SmsActivationPermissionPolicy.permissionsFor(
                SmsActivationDiagnostics.SmsRoleState.HELD,
                emptySet(),
            ),
        )
    }

    @Test
    fun `inbox path requests only read and receive permissions when sms role is held`() {
        val blockers = setOf(
            SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED,
            SmsActivationDiagnostics.Blocker.READ_PHONE_STATE_PERMISSION_REQUIRED,
            SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED,
            SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED,
        )
        val permissions = SmsInboxPermissionPolicy.permissionsFor(
            SmsActivationDiagnostics.SmsRoleState.HELD,
            blockers,
        )
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
            permissions,
        )
        assertFalse(permissions.contains(Manifest.permission.SEND_SMS))
        assertFalse(permissions.contains(Manifest.permission.READ_PHONE_STATE))
    }

    @Test
    fun `inbox path is fail closed unless sms role is held`() {
        val blockers = setOf(
            SmsActivationDiagnostics.Blocker.READ_SMS_PERMISSION_REQUIRED,
            SmsActivationDiagnostics.Blocker.RECEIVE_SMS_PERMISSION_REQUIRED,
        )
        assertArrayEquals(
            emptyArray<String>(),
            SmsInboxPermissionPolicy.permissionsFor(
                SmsActivationDiagnostics.SmsRoleState.AVAILABLE_NOT_HELD,
                blockers,
            ),
        )
        assertArrayEquals(
            emptyArray<String>(),
            SmsInboxPermissionPolicy.permissionsFor(
                SmsActivationDiagnostics.SmsRoleState.UNAVAILABLE,
                blockers,
            ),
        )
    }
}
