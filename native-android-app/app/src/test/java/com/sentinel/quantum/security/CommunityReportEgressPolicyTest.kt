package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CommunityReportEgressPolicyTest {
    @Test fun localOnlyRejectsEvenWithConsent() = assertDenied(ProtectionMode.LOCAL_ONLY, true)

    @Test fun enhancedRejectsWithoutExplicitConsent() = assertDenied(ProtectionMode.ENHANCED, false)

    @Test fun enhancedExplicitConsentPassesPolicy() {
        CommunityReportClient.requireEgressAllowed(ProtectionMode.ENHANCED, true)
    }

    private fun assertDenied(mode: ProtectionMode, consent: Boolean) {
        try {
            CommunityReportClient.requireEgressAllowed(mode, consent)
            fail("Remote egress must be denied")
        } catch (expected: SecurityException) {
            assertEquals("COMMUNITY_REPORT_REMOTE_EGRESS_DENIED", expected.message)
        }
    }
}
