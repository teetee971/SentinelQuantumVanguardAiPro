package com.sentinel.quantum.security

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the asynchronous authorization boundary without sending or persisting an SMS. */
@RunWith(AndroidJUnit4::class)
class SmsDeliveryRoleInstrumentationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Test
    fun queuedDeliveryRechecksTheRoleBeforeParsingThePdus() {
        var roleReads = 0
        var packageReads = 0
        val noLongerDefault = object : ContextWrapper(context) {
            override fun getPackageName(): String {
                packageReads++
                return "sentinel.instrumentation.not.default.sms"
            }

            override fun getSystemService(name: String): Any? {
                if (name == Context.ROLE_SERVICE) {
                    roleReads++
                    throw SecurityException("Role reader unavailable for this test")
                }
                return super.getSystemService(name)
            }
        }
        assertFalse(noLongerDefault.readSmsRoleStateFailClosed() == SmsActivationDiagnostics.SmsRoleState.HELD)
        roleReads = 0
        packageReads = 0
        val worker = SentinelSmsDeliverReceiver::class.java.getDeclaredMethod(
            "processDelivery", Context::class.java, Intent::class.java
        ).apply { isAccessible = true }
        // Missing PDUs would fail parsing if a stale onReceive authorization were retained.
        worker.invoke(SentinelSmsDeliverReceiver(), noLongerDefault, Intent(Telephony.Sms.Intents.SMS_DELIVER_ACTION))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) assertTrue(roleReads > 0)
        else assertTrue(packageReads > 0)
    }
}
