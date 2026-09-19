package com.sentinel.quantum.security

import org.junit.Test

class CallerReputationPrivacyFirewallTest {
    @Test(expected = SecurityException::class)
    fun localOnlyFailsClosedBeforeNetwork() {
        CallerReputationClient.requireEgressAllowed(PhonePrivacyFirewall.Mode.LOCAL_ONLY, true)
    }

    @Test(expected = SecurityException::class)
    fun enhancedWithoutConsentFailsClosedBeforeNetwork() {
        CallerReputationClient.requireEgressAllowed(PhonePrivacyFirewall.Mode.ENHANCED, false)
    }

    @Test
    fun enhancedWithExplicitConsentAllowsEgressGate() {
        CallerReputationClient.requireEgressAllowed(PhonePrivacyFirewall.Mode.ENHANCED, true)
    }
}
