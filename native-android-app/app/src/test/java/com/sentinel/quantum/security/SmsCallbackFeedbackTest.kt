package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsCallbackFeedbackTest {
    @Test fun storageFailureTakesPrecedenceOverSuccessfulRadioCallbacks() {
        val state = SmsCallbackProgress.State(1, sentOk = setOf(0), deliveredOk = setOf(0))
        val message = SmsCallbackFeedback.message(state, true)
        assertTrue(message.contains("enregistrement local"))
        assertFalse(message.contains("toutes les parties"))
    }

    @Test fun deliveryBeforeSentDoesNotAnnounceCompleteSuccess() {
        val state = SmsCallbackProgress.State(2, deliveredOk = setOf(0, 1))
        val message = SmsCallbackFeedback.message(state, false)
        assertTrue(message.contains("envoi complet à confirmer"))
        assertFalse(message.contains("toutes les parties"))
    }

    @Test fun completeSuccessRequiresBothStagesAndNoFailures() {
        val state = SmsCallbackProgress.State(1, sentOk = setOf(0), deliveredOk = setOf(0))
        assertTrue(SmsCallbackFeedback.message(state, false).contains("toutes les parties"))
        assertTrue(SmsCallbackFeedback.message(state.copy(deliveryFailed = setOf(0)), false).contains("Échec de livraison"))
    }
}

