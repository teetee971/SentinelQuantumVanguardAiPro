package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonePrivacyFirewallTest {
    @Test fun sensitiveClassesNeverLeaveDevice() {
        listOf(PhonePrivacyFirewall.DataClass.CONTACTS, PhonePrivacyFirewall.DataClass.CALL_HISTORY, PhonePrivacyFirewall.DataClass.OTP).forEach {
            assertFalse(PhonePrivacyFirewall.decide(PhonePrivacyFirewall.Mode.ENHANCED, it, true).mayLeaveDevice)
        }
    }

    @Test fun localOnlyAlwaysFailsClosed() {
        assertFalse(PhonePrivacyFirewall.decide(PhonePrivacyFirewall.Mode.LOCAL_ONLY, PhonePrivacyFirewall.DataClass.PHONE_NUMBER, true).mayLeaveDevice)
    }

    @Test fun enhancedStillRequiresExplicitConsent() {
        assertFalse(PhonePrivacyFirewall.decide(PhonePrivacyFirewall.Mode.ENHANCED, PhonePrivacyFirewall.DataClass.REPUTATION_QUERY, false).mayLeaveDevice)
        assertTrue(PhonePrivacyFirewall.decide(PhonePrivacyFirewall.Mode.ENHANCED, PhonePrivacyFirewall.DataClass.REPUTATION_QUERY, true).mayLeaveDevice)
    }
}
