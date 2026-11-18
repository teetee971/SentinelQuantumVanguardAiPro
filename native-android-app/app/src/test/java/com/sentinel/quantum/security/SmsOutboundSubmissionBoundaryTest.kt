package com.sentinel.quantum.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsOutboundSubmissionBoundaryTest {
    @Test fun smsChecksFreshAuthorizationAfterCallbacksAndBeforeEitherTransport() {
        val source = sender("SentinelSmsSender")
        val check = source.indexOf("checkOutboundAuthorization(prepared.subscriptionId")
        assertTrue("Recheck after durable provider and callback preparation", check > source.indexOf("PreparedCallbacks(sendToken, sent, delivered)"))
        assertTrue("Recheck before SmsManager text call", check in 1 until source.indexOf("prepared.manager.sendTextMessage("))
        assertTrue("Recheck before SmsManager multipart call", check in 1 until source.indexOf("prepared.manager.sendMultipartTextMessage("))
    }

    @Test fun mmsChecksFreshAuthorizationAfterProviderAndCallbacksBeforeTransport() {
        val source = sender("SentinelMmsSender")
        val check = source.indexOf("checkOutboundAuthorization(subscriptionId")
        assertTrue("Recheck after provider and callback preparation", check > source.indexOf("PendingIntent.getBroadcast("))
        assertTrue("Recheck before SmsManager MMS call", check in 1 until source.indexOf("manager.sendMultimediaMessage("))
    }

    @Test fun composerSubmitsThroughRetainedOwnerAndCorrelatesLateResultWithDraft() {
        val source = File("src/main/java/com/sentinel/quantum/SmsComposeActivity.kt").readText()
        assertTrue("Transport owner must survive configuration changes", source.contains("ViewModelProvider(this)[SmsComposeSubmissionViewModel::class.java]"))
        assertTrue("The current draft must own the returned submission", source.contains("completed.target.revision =="))
        assertTrue("Uncertain outcomes block immediate resubmission of the affected draft", source.contains("blocksDraft("))
    }

    @Test fun bothSendersCheckRoleAndPermissionsAroundTheSharedSimLookup() {
        for (name in listOf("SentinelSmsSender", "SentinelMmsSender")) {
            val authorization = sender(name).substringAfter("private fun checkOutboundAuthorization(")
                .substringBefore("private fun outboundPermissionDenial()")
            val first = authorization.indexOf("outboundPermissionDenial()")
            val lookup = authorization.indexOf("SmsSubscriptionState(context).load()")
            val last = authorization.lastIndexOf("outboundPermissionDenial()")
            assertTrue("Gate the protected SIM lookup and revalidate before transport in $name",
                first >= 0 && first < lookup && lookup < last)
            assertTrue("Fail closed on an unavailable SIM lookup in $name",
                authorization.contains("SmsSubscriptionState.Result.LookupFailed"))
        }
    }

    private fun sender(name: String): String = File("src/main/java/com/sentinel/quantum/security/$name.kt").readText()
}
