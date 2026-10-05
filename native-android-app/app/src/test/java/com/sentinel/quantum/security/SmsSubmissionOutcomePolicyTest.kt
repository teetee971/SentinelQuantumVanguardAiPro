package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SmsSubmissionOutcomePolicyTest {
    @Test fun synchronousSubmissionExceptionRemainsNonConclusive() {
        assertEquals(
            "TELEPHONY_SUBMISSION_OUTCOME_UNKNOWN",
            SmsSubmissionOutcomePolicy.reasonForSynchronousException()
        )
        assertFalse(SmsSubmissionOutcomePolicy.shouldMarkProviderFailedOnSynchronousException())
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
