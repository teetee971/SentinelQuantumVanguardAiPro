package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLinkSummaryTest {
    @Test fun freshnessExpiresAtThirtySeconds() {
        val summary = LocalLinkSummary(
            dnsServersPresent = true,
            defaultGatewayPresent = true,
            privateDnsActive = false,
            observedAtMillis = 1000L,
        )
        assertFalse(summary.isFresh(999L))
        assertTrue(summary.isFresh(1000L))
        assertTrue(summary.isFresh(30999L))
        assertFalse(summary.isFresh(31000L))
    }

    @Test fun missingDnsAndGatewayAreNotProofOfAttack() {
        val summary = LocalLinkSummary(false, false, false, 1000L)
        assertFalse(summary.dnsServersPresent)
        assertFalse(summary.defaultGatewayPresent)
        assertFalse(summary.privateDnsActive)
    }
    @Test fun unsupportedPrivateDnsIsNotInterpretedAsDisabled() {
        val summary = LocalLinkSummary(
            dnsServersPresent = false,
            defaultGatewayPresent = false,
            privateDnsActive = false,
            observedAtMillis = 1000L,
            privateDnsSupported = false,
        )
        assertFalse(summary.privateDnsSupported)
        assertFalse(summary.privateDnsActive)
        assertTrue(summary.isFresh(1500L))
    }
}
