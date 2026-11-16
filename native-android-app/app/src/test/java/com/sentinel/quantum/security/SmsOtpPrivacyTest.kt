package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsOtpPrivacyTest {
    @Test fun recognizesContextualOtpWithoutReturningSecret() {
        val result = SmsOtpPrivacy.inspect("Votre code de vérification est 482913. Ne le partagez pas.")
        assertTrue(result.containsOtp)
        assertEquals(6, result.codeLength)
        assertFalse(SmsOtpPrivacy.permitsRemoteTransmission(result))
    }

    @Test fun recognizesAlphanumericOtpWithoutReturningSecret() {
        val result = SmsOtpPrivacy.inspect("Votre code de vérification est A7K9Q2")
        assertTrue(result.containsOtp)
        assertEquals(6, result.codeLength)
        assertFalse(SmsOtpPrivacy.permitsRemoteTransmission(result))
    }

    @Test fun ordinaryWordNearCodeLanguageIsNotTreatedAsOtp() {
        val result = SmsOtpPrivacy.inspect("Votre code de confirmation arrive DEMAIN")
        assertFalse(result.containsOtp)
    }

    @Test fun ordinaryNumberIsNotTreatedAsOtp() {
        val result = SmsOtpPrivacy.inspect("Appelez le 0612345678 demain.")
        assertFalse(result.containsOtp)
        assertTrue(SmsOtpPrivacy.permitsRemoteTransmission(result))
    }

    @Test fun codeLanguageWithoutNumericTokenIsNotOtp() {
        val result = SmsOtpPrivacy.inspect("Votre code de confirmation arrive bientôt.")
        assertFalse(result.containsOtp)
    }
}
