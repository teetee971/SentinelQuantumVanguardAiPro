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
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MmsPreTransportRevocationInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val packageName = context.packageName

    @After
    fun cleanupAuthorizationAndHook() {
        runCatching { MmsPreTransportTestInterlock.installForInstrumentation(null) }
        runCatching { shell("appops set --user 0 --uid $packageName SEND_SMS allow") }
        runCatching { shell("appops set --user 0 $packageName SEND_SMS allow") }
    }

    @Test
    fun sendMms_revokedAtFinalBoundary_neverStartsTransportAndRemovesPreparedState() {
        assumeTrue("ROLE_SMS shell contract starts on Android 10", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        assumeTrue(
            "MMS race proof requires telephony messaging feature",
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)
        )

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
        assertTrue("Emulator must expose at least one active MMS subscription", subscriptions.isNotEmpty())
        val subscriptionId = subscriptions.first().subscriptionId
        val timelineBefore = outgoingMmsTimelineCount()

        MmsPreTransportTestInterlock.installForInstrumentation {
            shell("appops set --user 0 --uid $packageName SEND_SMS ignore")
            shell("appops set --user 0 $packageName SEND_SMS ignore")
            val appOps = shell("appops get $packageName SEND_SMS")
            assertTrue(
                "SEND_SMS AppOp denial must be observable before MMS final revalidation: $appOps",
                appOps.contains("ignore", ignoreCase = true) ||
                    appOps.contains("deny", ignoreCase = true) ||
                    appOps.contains("errored", ignoreCase = true)
            )
        }

        val result = SentinelMmsSender(context).send(
            destination = "+15550124",
            text = "SentinelMmsPreTransportRaceProbe",
            requestedSubscriptionId = subscriptionId,
            attachments = emptyList()
        )

        assertFalse(result.accepted)
        assertEquals("SEND_SMS_PERMISSION_NOT_GRANTED", result.reason)
        assertNotNull(result.providerMessageId)
        assertNotNull(result.token)
        val providerMessageId = requireNotNull(result.providerMessageId)
        val token = requireNotNull(result.token)

        val providerUri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerMessageId)
        context.contentResolver.query(
            providerUri,
            arrayOf(Telephony.Mms._ID),
            null,
            null,
            null
        )!!.use { cursor ->
            assertFalse("Pre-transport MMS provider row must be removed", cursor.moveToFirst())
        }

        val stagedPdu = File(File(context.cacheDir, "sentinel_mms_send"), "$token.pdu")
        assertFalse("Pre-transport MMS PDU must be deleted", stagedPdu.exists())

        Thread.sleep(750)
        assertEquals(
            "No MMS transport callback may be recorded after pre-transport rejection",
            timelineBefore,
            outgoingMmsTimelineCount()
        )
    }

    private fun outgoingMmsTimelineCount(): Int =
        PhonePrivateTimelineStore(context).read().events.count {
            it.kind == PhonePrivateTimeline.Kind.MMS && it.direction == "OUTGOING"
        }

    private fun waitForSmsRole() {
        repeat(30) {
            if (SentinelSmsSender(context).holdsSmsRole()) return
            Thread.sleep(100)
        }
        assertTrue("Sentinel must hold ROLE_SMS before the MMS race probe", SentinelSmsSender(context).holdsSmsRole())
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }
}
