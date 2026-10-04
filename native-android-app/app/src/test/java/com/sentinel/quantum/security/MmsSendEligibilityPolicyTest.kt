package com.sentinel.quantum.security

import android.telephony.SubscriptionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSendEligibilityPolicyTest {
    private val held = SmsActivationDiagnostics.SmsRoleState.HELD

    @Test fun acceptsBoundedImageRequest() {
        assertTrue(
            MmsSendEligibilityPolicy.evaluate(
                held, 1, "+33612345678", "Photo",
                listOf(MmsSendEligibilityPolicy.Attachment("image/jpeg", 1024))
            ) is MmsSendEligibilityPolicy.Result.Eligible
        )
    }

    @Test fun failsClosedWithoutSmsRole() {
        assertEquals(
            "SMS_ROLE_NOT_HELD",
            rejected(
                SmsActivationDiagnostics.SmsRoleState.AVAILABLE_NOT_HELD,
                1, "+33612345678", "x", emptyList()
            )
        )
    }

    @Test fun requiresExplicitSubscription() {
        assertEquals(
            "MMS_SUBSCRIPTION_REQUIRED",
            rejected(held, SubscriptionManager.INVALID_SUBSCRIPTION_ID, "+33612345678", "x", emptyList())
        )
    }

    @Test fun requiresCanonicalInternationalDestinationAndRejectsEmptyRequest() {
        assertEquals("E164_DESTINATION_REQUIRED", rejected(held, 1, "123", "x", emptyList()))
        assertEquals("E164_DESTINATION_REQUIRED", rejected(held, 1, "0690123456", "x", emptyList()))
        assertEquals("EMPTY_MMS", rejected(held, 1, "+590690123456", "", emptyList()))
    }

    @Test fun rejectsUnsupportedOrOversizedAttachments() {
        assertEquals(
            "UNSUPPORTED_MIME_TYPE",
            rejected(held, 1, "+33612345678", "", listOf(MmsSendEligibilityPolicy.Attachment("application/pdf", 100)))
        )
        assertEquals(
            "ATTACHMENT_SIZE_REJECTED",
            rejected(held, 1, "+33612345678", "", listOf(MmsSendEligibilityPolicy.Attachment("image/png", MmsSendEligibilityPolicy.MAX_ATTACHMENT_BYTES + 1)))
        )
    }

    private fun rejected(
        role: SmsActivationDiagnostics.SmsRoleState,
        subscriptionId: Int,
        destination: String,
        text: String,
        attachments: List<MmsSendEligibilityPolicy.Attachment>
    ): String = (MmsSendEligibilityPolicy.evaluate(role, subscriptionId, destination, text, attachments)
        as MmsSendEligibilityPolicy.Result.Rejected).reason
}
