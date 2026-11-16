package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsOtpPrivacyBoundaryTest {
    @Test fun contextualOtpIsNeverPermittedForRemoteTransmission() {
        val result = SmsOtpPrivacy.inspect("OTP 739204 valable cinq minutes")
        assertTrue(result.containsOtp)
        assertFalse(SmsOtpPrivacy.permitsRemoteTransmission(result))
    }

    @Test fun ordinaryLongReferenceIsNotClassifiedAsOtp() {
        val result = SmsOtpPrivacy.inspect("Référence de commande 123456789")
        assertFalse(result.containsOtp)
        assertNull(result.codeLength)
        assertTrue(SmsOtpPrivacy.permitsRemoteTransmission(result))
    }

    @Test fun phoneNumberWithoutOtpContextIsNotClassifiedAsSecret() {
        val result = SmsOtpPrivacy.inspect("Appelez le 0612345678 pour le rendez-vous")
        assertFalse(result.containsOtp)
        assertTrue(SmsOtpPrivacy.permitsRemoteTransmission(result))
    }

    @Test fun otpResultExposesOnlyLengthNotSecretValue() {
        val result = SmsOtpPrivacy.inspect("Votre code de vérification est 482913")
        assertTrue(result.containsOtp)
        assertTrue(result.codeLength == 6)
        assertFalse(result.toString().contains("482913"))
    }
}
