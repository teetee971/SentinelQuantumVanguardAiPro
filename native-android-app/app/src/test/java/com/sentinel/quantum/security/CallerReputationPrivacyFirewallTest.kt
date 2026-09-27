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

    @Test(expected = SecurityException::class)
    fun dynamicGateFailsClosedAfterConsentIsRevoked() {
        var allowed = true
        val gate = { allowed }
        CallerReputationClient.requireDynamicEgressAllowed(gate)
        allowed = false
        CallerReputationClient.requireDynamicEgressAllowed(gate)
    }

    @Test(expected = SecurityException::class)
    fun dynamicGateFailsClosedWhenStateReadThrows() {
        CallerReputationClient.requireDynamicEgressAllowed { throw IllegalStateException("state unavailable") }
    }

    @Test
    fun dynamicGateAllowsCurrentExplicitState() {
        CallerReputationClient.requireDynamicEgressAllowed { true }
    }

    @Test
    fun enhancedWithExplicitConsentAllowsEgressGate() {
        CallerReputationClient.requireEgressAllowed(PhonePrivacyFirewall.Mode.ENHANCED, true)
    }
}
