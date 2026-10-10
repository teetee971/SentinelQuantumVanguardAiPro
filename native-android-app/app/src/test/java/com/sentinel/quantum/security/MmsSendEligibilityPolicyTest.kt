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
        assertEquals(
            "MMS_SUBSCRIPTION_REQUIRED",
            rejected(held, -2, "+33612345678", "x", emptyList())
        )
    }

    @Test fun rejectsInvalidDestinationAndEmptyRequest() {
        assertEquals("INVALID_DESTINATION", rejected(held, 1, "123", "x", emptyList()))
        assertEquals("EMPTY_MMS", rejected(held, 1, "+33612345678", "", emptyList()))
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
