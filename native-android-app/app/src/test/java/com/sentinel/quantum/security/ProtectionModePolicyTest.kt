package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionModePolicyTest {
    @Test fun localOnlyNeverPermitsCallerNumberEnrichment() {
        assertFalse(ProtectionModePolicy.permitsCallerNumberEnrichment(ProtectionMode.LOCAL_ONLY))
    }

    @Test fun enhancedMayPermitCallerNumberEnrichment() {
        assertTrue(ProtectionModePolicy.permitsCallerNumberEnrichment(ProtectionMode.ENHANCED))
    }

    @Test fun otpBodyRemainsLocalEvenInEnhancedMode() {
        val otp = SmsOtpPrivacy.inspect("Votre OTP est 482913")
        assertTrue(otp.containsOtp)
        assertFalse(ProtectionModePolicy.permitsSmsBodyTransmission(ProtectionMode.ENHANCED, otp))
    }

    @Test fun contactsAndCallHistoryNeverBecomeRemotePayloads() {
        ProtectionMode.entries.forEach { mode ->
            assertFalse(ProtectionModePolicy.permitsContactsTransmission(mode))
            assertFalse(ProtectionModePolicy.permitsCallHistoryTransmission(mode))
        }
    }
}
