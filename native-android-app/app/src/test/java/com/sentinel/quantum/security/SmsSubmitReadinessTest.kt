package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSubmitReadinessTest {
    @Test fun blocksMultiSimUntilExplicitLineSelected() {
        assertFalse(SmsSubmitReadiness.canSubmit(true, listOf(1, 2), null, true, true, false))
        assertTrue(SmsSubmitReadiness.canSubmit(true, listOf(1, 2), 2, true, true, false))
    }

    @Test fun singleActiveLineDoesNotRequireManualSelection() {
        assertTrue(SmsSubmitReadiness.canSubmit(true, listOf(7), null, true, true, false))
    }

    @Test fun blocksStaleSelectionAndMissingActiveSim() {
        assertFalse(SmsSubmitReadiness.canSubmit(true, listOf(1), 2, true, true, false))
        assertFalse(SmsSubmitReadiness.canSubmit(true, emptyList(), null, true, true, false))
    }

    @Test fun preservesActivationContentAndMmsGates() {
        assertFalse(SmsSubmitReadiness.canSubmit(false, listOf(1), 1, true, true, false))
        assertFalse(SmsSubmitReadiness.canSubmit(true, listOf(1), 1, false, true, false))
        assertFalse(SmsSubmitReadiness.canSubmit(true, listOf(1), 1, true, false, false))
        assertFalse(SmsSubmitReadiness.canSubmit(true, listOf(1), 1, true, true, true))
    }
}
