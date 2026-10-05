package com.sentinel.quantum.security

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MissedCallReceiverManifestInstrumentationTest {
    @Test
    fun missedCallReceiverIsExportedOnlyBehindTelecomSignaturePermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, SentinelMissedCallReceiver::class.java),
            0
        )

        assertTrue(receiver.exported)
        assertEquals("android.permission.MODIFY_PHONE_STATE", receiver.permission)
    }
}