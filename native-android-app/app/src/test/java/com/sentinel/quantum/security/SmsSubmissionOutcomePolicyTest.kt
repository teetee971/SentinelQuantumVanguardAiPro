package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SmsSubmissionOutcomePolicyTest {
    @Test fun synchronousSubmissionExceptionRemainsNonConclusive() {
        assertEquals(
            "TELEPHONY_SUBMISSION_OUTCOME_UNKNOWN",
            SmsSubmissionOutcomePolicy.reasonForSynchronousException()
        )
        assertFalse(SmsSubmissionOutcomePolicy.shouldMarkProviderFailedOnSynchronousException())
    }
}
