package com.sentinel.quantum.security

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneCoreRuntimeRevocationInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val packageName = context.packageName

    @Test
    fun smsRoleAndSendPermissionRevocationRemainFailClosed() {
        try {
            shell("cmd role add-role-holder --user 0 android.app.role.SMS $packageName")
            shell("pm grant $packageName ${Manifest.permission.SEND_SMS}")
            shell("pm grant $packageName ${Manifest.permission.READ_PHONE_STATE}")
            waitUntil { context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD }

            shell("cmd role remove-role-holder --user 0 android.app.role.SMS $packageName")
            waitUntil { context.readSmsRoleStateFailClosed() != SmsActivationDiagnostics.SmsRoleState.HELD }
            val withoutRole = SmsActivationDiagnostics(context).snapshot()
            assertNotEquals(SmsActivationDiagnostics.SmsRoleState.HELD, withoutRole.smsRoleState)
            assertFalse(withoutRole.canSend)

            shell("cmd role add-role-holder --user 0 android.app.role.SMS $packageName")
            waitUntil { context.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD }
            shell("pm revoke $packageName ${Manifest.permission.SEND_SMS}")
            waitUntil {
                ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
                    PackageManager.PERMISSION_GRANTED
            }
            val withoutSendPermission = SmsActivationDiagnostics(context).snapshot()
            assertTrue(
                SmsActivationDiagnostics.Blocker.SEND_SMS_PERMISSION_REQUIRED in
                    withoutSendPermission.blockers
            )
            assertFalse(withoutSendPermission.canSend)
        } finally {
            shell("cmd role add-role-holder --user 0 android.app.role.SMS $packageName")
            shell("pm grant $packageName ${Manifest.permission.SEND_SMS}")
            shell("pm grant $packageName ${Manifest.permission.READ_PHONE_STATE}")
        }
    }

    @Test
    fun dialerAndCallScreeningRolesReflectActualAndroidRevocation() {
        val roleManager = context.getSystemService(RoleManager::class.java)
        try {
            shell("cmd role add-role-holder --user 0 android.app.role.DIALER $packageName")
            shell("cmd role add-role-holder --user 0 android.app.role.CALL_SCREENING $packageName")
            waitUntil { roleManager.isRoleHeld(RoleManager.ROLE_DIALER) }
            waitUntil { roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }

            shell("cmd role remove-role-holder --user 0 android.app.role.CALL_SCREENING $packageName")
            waitUntil { !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }
            assertFalse(roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))

            shell("cmd role remove-role-holder --user 0 android.app.role.DIALER $packageName")
            waitUntil { !roleManager.isRoleHeld(RoleManager.ROLE_DIALER) }
            assertFalse(roleManager.isRoleHeld(RoleManager.ROLE_DIALER))
        } finally {
            shell("cmd role add-role-holder --user 0 android.app.role.DIALER $packageName")
            shell("cmd role add-role-holder --user 0 android.app.role.CALL_SCREENING $packageName")
        }
    }

    private fun waitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return
            Thread.sleep(100)
        }
        assertTrue("Android state did not converge before timeout", condition())
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use {
            FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }
}
