package com.sentinel.quantum

import android.content.ComponentName
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the installed application's merged-manifest security contract on a real Android runtime.
 * Static source checks remain useful, but these assertions prove what PackageManager actually sees.
 */
@RunWith(AndroidJUnit4::class)
class PhoneCoreManifestContractInstrumentationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private fun component(relativeName: String): ComponentName =
        ComponentName(context.packageName, context.packageName + relativeName)

    @Test
    fun privilegedPhoneAndMessagingEntrypointsRequireSystemPermissions() {
        val packageManager = context.packageManager

        val callScreening = packageManager.getServiceInfo(
            component(".security.SentinelCallScreeningService"),
            0
        )
        assertTrue(callScreening.exported)
        assertEquals("android.permission.BIND_SCREENING_SERVICE", callScreening.permission)

        val inCall = packageManager.getServiceInfo(
            component(".security.SentinelInCallService"),
            0
        )
        assertTrue(inCall.exported)
        assertEquals("android.permission.BIND_INCALL_SERVICE", inCall.permission)

        val respondViaMessage = packageManager.getServiceInfo(
            component(".security.SentinelRespondViaMessageService"),
            0
        )
        assertTrue(respondViaMessage.exported)
        assertEquals("android.permission.SEND_RESPOND_VIA_MESSAGE", respondViaMessage.permission)

        val smsDeliver = packageManager.getReceiverInfo(
            component(".security.SentinelSmsDeliverReceiver"),
            0
        )
        assertTrue(smsDeliver.exported)
        assertEquals("android.permission.BROADCAST_SMS", smsDeliver.permission)

        val mmsDeliver = packageManager.getReceiverInfo(
            component(".security.SentinelMmsDeliverReceiver"),
            0
        )
        assertTrue(mmsDeliver.exported)
        assertEquals("android.permission.BROADCAST_WAP_PUSH", mmsDeliver.permission)
    }

    @Test
    fun internalPhoneCoreSurfacesRemainNonExported() {
        val packageManager = context.packageManager

        listOf(
            ".VoiceStudioActivity",
            ".PhoneCoreActivationActivity",
            ".PhoneCoreDiagnosticActivity",
            ".SentinelInCallActivity",
            ".CallerIdActivity"
        ).forEach { name ->
            assertFalse(
                "$name must remain non-exported",
                packageManager.getActivityInfo(component(name), 0).exported
            )
        }

        listOf(
            ".security.SentinelCallActionReceiver",
            ".security.SentinelMissedCallReceiver",
            ".security.SentinelMmsDownloadReceiver",
            ".security.SentinelMmsSendStatusReceiver",
            ".security.SentinelSmsStatusReceiver"
        ).forEach { name ->
            assertFalse(
                "$name must remain non-exported",
                packageManager.getReceiverInfo(component(name), 0).exported
            )
        }
    }

    @Test
    fun intentionalUserEntrypointsRemainExported() {
        val packageManager = context.packageManager

        listOf(
            ".MainActivity",
            ".SentinelDialerActivity",
            ".SmsComposeActivity"
        ).forEach { name ->
            assertTrue(
                "$name must remain exported for its declared user/system intent",
                packageManager.getActivityInfo(component(name), 0).exported
            )
        }
    }

    @Test
    fun fileProviderRemainsPrivateAndGrantScoped() {
        val provider = context.packageManager.resolveContentProvider(
            "${context.packageName}.fileprovider",
            0
        )
        assertNotNull(provider)
        requireNotNull(provider)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
    }
}
