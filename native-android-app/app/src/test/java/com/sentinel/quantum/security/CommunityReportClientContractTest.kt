package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityReportClientContractTest {
    @Test fun categoriesRemainBoundedToModeratedCallSignals() {
        val names = CommunityReportClient.Category.entries.map { it.name }.toSet()
        assertTrue("WANGIRI" in names)
        assertTrue("SPOOFING" in names)
        assertTrue("PREMIUM_RATE" in names)
        assertTrue("ROBOCALL" in names)
        assertFalse("FRAUD_CONFIRMED" in names)
        assertFalse("IDENTITY_VERIFIED" in names)
    }

    @Test fun publicEndpointDoesNotEmbedServerCredentialInItsUrl() {
        val endpoint = CommunityReportClient.ENDPOINT
        assertTrue(endpoint.startsWith("https://"))
        assertFalse(endpoint.contains("api_key", ignoreCase = true))
        assertFalse(endpoint.contains("token", ignoreCase = true))
        assertFalse(endpoint.contains("secret", ignoreCase = true))
    }

    @Test fun communityReportClientDeniesLocalModeAndMissingConsent() {
        val denied = listOf(
            PhonePrivacyFirewall.Mode.LOCAL_ONLY to false,
            PhonePrivacyFirewall.Mode.LOCAL_ONLY to true,
            PhonePrivacyFirewall.Mode.ENHANCED to false
        )
        denied.forEach { (mode, consent) ->
            try {
                CommunityReportClient.requireEgressAllowed(mode, consent)
                throw AssertionError("Expected remote egress denial")
            } catch (expected: SecurityException) {
                assertTrue(expected.message == "COMMUNITY_REPORT_REMOTE_EGRESS_DENIED")
            }
        }
        CommunityReportClient.requireEgressAllowed(PhonePrivacyFirewall.Mode.ENHANCED, true)
    }
}
