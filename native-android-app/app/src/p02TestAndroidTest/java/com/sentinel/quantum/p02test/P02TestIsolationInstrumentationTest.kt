package com.sentinel.quantum.p02test

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Runtime guard: these tests must never run against the production package. */
@RunWith(AndroidJUnit4::class)
class P02TestIsolationInstrumentationTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun target_is_the_dedicated_p02_package() {
        val target = instrumentation.targetContext
        assertEquals("com.sentinel.quantum.p02test", target.packageName)
        assertNotEquals("com.sentinel.quantum", target.packageName)
        assertNotEquals("com.sentinel.quantum.labpreview", target.packageName)
    }

    @Test
    fun target_manifest_has_no_sensitive_roles_or_runtime_data_plane() {
        val target = instrumentation.targetContext
        val packageInfo = target.packageManager.getPackageInfo(
            target.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        listOf(
            "android.permission.CALL_PHONE",
            "android.permission.READ_CALL_LOG",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS",
            "android.permission.RECORD_AUDIO"
        ).forEach { permission -> assertFalse(permission in permissions) }

        assertNull(target.packageManager.resolveService(
            Intent("android.telecom.CallScreeningService")
                .setPackage(target.packageName),
            PackageManager.MATCH_ALL
        ))
        assertNull(target.packageManager.resolveService(
            Intent("android.telecom.InCallService")
                .setPackage(target.packageName),
            PackageManager.MATCH_ALL
        ))
        assertNull(target.packageManager.resolveActivity(
            Intent(Intent.ACTION_SENDTO).setPackage(target.packageName).setData(android.net.Uri.parse("sms:")),
            PackageManager.MATCH_ALL
        ))
    }
}
