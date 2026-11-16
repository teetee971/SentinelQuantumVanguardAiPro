package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsSubmissionOutcomePolicyTest {
    @Test fun failuresBeforeTheSmsManagerBoundaryRemainConclusive() {
        assertEquals(
            "SMS_TELEPHONY_PREPARATION_FAILED",
            SmsSubmissionOutcomePolicy.reasonForPreparationException()
        )
        assertEquals(
            "SMS_CALLBACK_PREPARATION_FAILED",
            SmsSubmissionOutcomePolicy.reasonForCallbackPreparationException(true)
        )
        assertEquals(
            "SMS_CALLBACK_PREPARATION_FAILED_PROVIDER_REPAIR_FAILED",
            SmsSubmissionOutcomePolicy.reasonForCallbackPreparationException(false)
        )
    }

    @Test fun actualSubmissionExceptionRemainsNonConclusive() {
        assertEquals(
            "TELEPHONY_SUBMISSION_OUTCOME_UNKNOWN",
            SmsSubmissionOutcomePolicy.reasonForSubmissionException()
        )
    }

    @Test fun submissionsMustFitTheDurableMultipartCallbackBoundary() {
        assertNull(SmsSubmissionOutcomePolicy.reasonForPartCount(1))
        assertNull(SmsSubmissionOutcomePolicy.reasonForPartCount(SmsCallbackProgress.MAX_PARTS))
        for (partCount in listOf(0, SmsCallbackProgress.MAX_PARTS + 1, 299, Int.MAX_VALUE)) {
            assertEquals(
                "SMS_MULTIPART_LIMIT_EXCEEDED",
                SmsSubmissionOutcomePolicy.reasonForPartCount(partCount)
            )
            assertNull(SmsCallbackProgress.record(null, 0, partCount, SmsDeliveryStatusBus.Stage.SENT, true))
        }
    }
}
