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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { shell("cmd role add-role-holder --user 0 android.app.role.SMS $packageName") }
        }
        runCatching { shell("appops set --user 0 --uid $packageName SEND_SMS allow") }
        runCatching { shell("appops set --user 0 $packageName SEND_SMS allow") }
    }

    @Test
    fun sendSms_revokedAtFinalBoundary_neverCrossesTelephonyAndRepairsProvider() {
        assumeTrue("ROLE_SMS shell contract starts on Android 10", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        prepareAuthorizedSmsState()
        val subscriptionId = activeSubscriptionId()

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
        val providerMessageId = requireNotNull(result.providerMessageId)
        assertProviderFailed(providerMessageId)
        assertNoTransportCallbacks(providerMessageId, "pre-transport rejection")
        runCatching { context.contentResolver.delete(messageUri(providerMessageId), null, null) }
    }

    @Test
    fun sendSms_roleRevokedAtFinalBoundary_recoversOnlyAfterRoleRestoration() {
        assumeTrue("ROLE_SMS shell contract starts on Android 10", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        prepareAuthorizedSmsState()
        val subscriptionId = activeSubscriptionId()

        SmsPreTransportTestInterlock.installForInstrumentation {
            shell("cmd role remove-role-holder --user 0 android.app.role.SMS $packageName")
            waitForSmsRoleAbsent()
        }

        val result = SentinelSmsSender(context).send(
            destination = "+15550124",
            body = "SentinelRoleRevocationRecoveryProbe",
            requestedSubscriptionId = subscriptionId
        )

        assertFalse(result.accepted)
        assertEquals(
            SentinelSmsSender.PRE_SUBMIT_REVALIDATION_PROVIDER_REPAIR_FAILED,
            result.reason
        )
        val providerMessageId = requireNotNull(result.providerMessageId)
        val journal = SmsPreSubmitJournal(context)
        val pending = journal.all().singleOrNull { it.providerMessageId == providerMessageId }
        assertNotNull("Role-revoked pre-transport row must remain durably recoverable", pending)
        assertEquals(SmsPreSubmitJournal.Phase.PROVIDER_READY, pending!!.phase)

        SmsPreTransportTestInterlock.installForInstrumentation(null)
        shell("cmd role add-role-holder --user 0 android.app.role.SMS $packageName")
        waitForSmsRole()
        SmsPreSubmitRecoveryWorker.scheduleStartupRecovery(context)
        waitForProviderFailedAndJournalCleared(providerMessageId)

        assertNoTransportCallbacks(
            providerMessageId,
            "role-revoked pre-transport rejection"
        )
        runCatching { context.contentResolver.delete(messageUri(providerMessageId), null, null) }
    }

    private fun prepareAuthorizedSmsState() {
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
    }

    private fun activeSubscriptionId(): Int {
        val subscriptions = context.getSystemService(SubscriptionManager::class.java)
            .activeSubscriptionInfoList
            .orEmpty()
            .filter { it.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        assertTrue("Emulator must expose at least one active SMS subscription", subscriptions.isNotEmpty())
        return subscriptions.first().subscriptionId
    }

    private fun waitForSmsRole() {
        repeat(40) {
            if (SentinelSmsSender(context).holdsSmsRole()) return
            Thread.sleep(100)
        }
        assertTrue("Sentinel must hold ROLE_SMS before the race probe", SentinelSmsSender(context).holdsSmsRole())
    }

    private fun waitForSmsRoleAbsent() {
        repeat(40) {
            if (!SentinelSmsSender(context).holdsSmsRole()) return
            Thread.sleep(100)
        }
        assertFalse("ROLE_SMS removal must be observable at the final boundary", SentinelSmsSender(context).holdsSmsRole())
    }

    private fun waitForProviderFailedAndJournalCleared(providerMessageId: Long) {
        repeat(80) {
            val failed = providerState(providerMessageId)?.let { state ->
                state.first == Telephony.Sms.MESSAGE_TYPE_FAILED &&
                    state.second == Telephony.Sms.STATUS_FAILED
            } == true
            val journalCleared = SmsPreSubmitJournal(context).all().none {
                it.providerMessageId == providerMessageId
            }
            if (failed && journalCleared) return
            Thread.sleep(100)
        }
        assertProviderFailed(providerMessageId)
        assertTrue(
            "Recovered provider row must no longer have a pre-submit journal record",
            SmsPreSubmitJournal(context).all().none { it.providerMessageId == providerMessageId }
        )
    }

    private fun assertProviderFailed(providerMessageId: Long) {
        val state = providerState(providerMessageId)
        assertNotNull("Compensated provider row must still be readable", state)
        assertEquals(Telephony.Sms.MESSAGE_TYPE_FAILED, state!!.first)
        assertEquals(Telephony.Sms.STATUS_FAILED, state.second)
    }

    private fun providerState(providerMessageId: Long): Pair<Int, Int>? =
        context.contentResolver.query(
            messageUri(providerMessageId),
            arrayOf(Telephony.Sms.TYPE, Telephony.Sms.STATUS),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getInt(0) to cursor.getInt(1)
        }

    private fun assertNoTransportCallbacks(providerMessageId: Long, label: String) {
        Thread.sleep(750)
        val callbacksForMessage = SmsDeliveryStatusBus.events.replayCache
            .filter { it.providerMessageId == providerMessageId }
        val sentCallbackCount = callbacksForMessage.count { it.stage == SmsDeliveryStatusBus.Stage.SENT }
        val deliveredCallbackCount = callbacksForMessage.count { it.stage == SmsDeliveryStatusBus.Stage.DELIVERED }
        assertEquals("No SENT callback may exist for a $label", 0, sentCallbackCount)
        assertEquals("No DELIVERED callback may exist for a $label", 0, deliveredCallbackCount)
    }

    private fun messageUri(providerMessageId: Long) =
        ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, providerMessageId)

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }
}
