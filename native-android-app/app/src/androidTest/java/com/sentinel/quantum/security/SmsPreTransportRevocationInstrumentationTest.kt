package com.sentinel.quantum.security

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsPreTransportRevocationInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val packageName = context.packageName

    @After
    fun cleanupAuthorizationAndHook() {
        runCatching { SmsPreTransportTestInterlock.installForInstrumentation(null) }
        runCatching { shell("appops set --user 0 --uid $packageName SEND_SMS allow") }
        runCatching { shell("appops set --user 0 $packageName SEND_SMS allow") }
    }

    @Test
    fun sendSms_revokedAtFinalBoundary_neverCrossesTelephonyAndRepairsProvider() {
        assumeTrue("ROLE_SMS shell contract starts on Android 10", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

        shell("cmd role add-role-holder --user 0 android.app.role.SMS $packageName")
        shell("pm grant $packageName ${Manifest.permission.SEND_SMS}")
        shell("pm grant $packageName ${Manifest.permission.READ_PHONE_STATE}")
        shell("appops set --user 0 --uid $packageName SEND_SMS allow")
        shell("appops set --user 0 $packageName SEND_SMS allow")

        waitForSmsRole()
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
        )
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
        )

        val subscriptions = context.getSystemService(SubscriptionManager::class.java)
            .activeSubscriptionInfoList
            .orEmpty()
            .filter { it.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        assertTrue("Emulator must expose at least one active SMS subscription", subscriptions.isNotEmpty())
        val subscriptionId = subscriptions.first().subscriptionId

        SmsPreTransportTestInterlock.installForInstrumentation {
            shell("appops set --user 0 --uid $packageName SEND_SMS ignore")
            shell("appops set --user 0 $packageName SEND_SMS ignore")
            val appOps = shell("appops get $packageName SEND_SMS")
            assertTrue(
                "SEND_SMS AppOp denial must be observable before final revalidation: $appOps",
                appOps.contains("ignore", ignoreCase = true) ||
                    appOps.contains("deny", ignoreCase = true) ||
                    appOps.contains("errored", ignoreCase = true)
            )
        }

        val result = SentinelSmsSender(context).send(
            destination = "+15550123",
            body = "SentinelPreTransportRaceProbe",
            requestedSubscriptionId = subscriptionId
        )

        assertFalse(result.accepted)
        assertEquals("SEND_SMS_PERMISSION_NOT_GRANTED", result.reason)
        assertNotNull(result.providerMessageId)
        val providerMessageId = requireNotNull(result.providerMessageId)

        val messageUri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, providerMessageId)
        context.contentResolver.query(
            messageUri,
            arrayOf(Telephony.Sms.TYPE, Telephony.Sms.STATUS),
            null,
            null,
            null
        )!!.use { cursor ->
            assertTrue("Compensated provider row must still be readable", cursor.moveToFirst())
            assertEquals(Telephony.Sms.MESSAGE_TYPE_FAILED, cursor.getInt(0))
            assertEquals(Telephony.Sms.STATUS_FAILED, cursor.getInt(1))
        }

        Thread.sleep(750)
        val callbacksForMessage = SmsDeliveryStatusBus.events.replayCache
            .filter { it.providerMessageId == providerMessageId }
        val sentCallbackCount = callbacksForMessage.count { it.stage == SmsDeliveryStatusBus.Stage.SENT }
        val deliveredCallbackCount = callbacksForMessage.count { it.stage == SmsDeliveryStatusBus.Stage.DELIVERED }
        assertEquals("No SENT callback may exist for a pre-transport rejection", 0, sentCallbackCount)
        assertEquals("No DELIVERED callback may exist for a pre-transport rejection", 0, deliveredCallbackCount)

        runCatching { context.contentResolver.delete(messageUri, null, null) }
    }

    private fun waitForSmsRole() {
        repeat(30) {
            if (SentinelSmsSender(context).holdsSmsRole()) return
            Thread.sleep(100)
        }
        assertTrue("Sentinel must hold ROLE_SMS before the race probe", SentinelSmsSender(context).holdsSmsRole())
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }
}
