package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSubmitReadinessTest {
    @Test fun blocksMultiSimUntilExplicitLineSelected() {
        assertFalse(SmsSubmitReadiness.canSubmit(true, listOf(1, 2), null, true, true))
        assertTrue(SmsSubmitReadiness.canSubmit(true, listOf(1, 2), 2, true, true))
    }

    @Test fun singleActiveLineDoesNotRequireManualSelection() {
        assertTrue(SmsSubmitReadiness.canSubmit(true, listOf(7), null, true, true))
    }

    @Test fun blocksStaleSelectionAndMissingActiveSim() {
        assertFalse(SmsSubmitReadiness.canSubmit(true, listOf(1), 2, true, true))
        assertFalse(SmsSubmitReadiness.canSubmit(true, emptyList(), null, true, true))
    }

    @Test fun preservesActivationAndContentGatesForSmsAndMmsTransport() {
        assertFalse(SmsSubmitReadiness.canSubmit(false, listOf(1), 1, true, true))
        assertFalse(SmsSubmitReadiness.canSubmit(true, listOf(1), 1, false, true))
        assertFalse(SmsSubmitReadiness.canSubmit(true, listOf(1), 1, true, false))
        assertTrue(SmsSubmitReadiness.canSubmit(true, listOf(1), 1, true, true))
    }

    @Test fun blocksConcurrentSubmissionUntilThePreviousRequestResolves() {
        assertFalse(
            SmsSubmitReadiness.canSubmit(
                true,
                listOf(1),
                1,
                true,
                true,
                submissionInFlight = true
            )
        )
    }
}
