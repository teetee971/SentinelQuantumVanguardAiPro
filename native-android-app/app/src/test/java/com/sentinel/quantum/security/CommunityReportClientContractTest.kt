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
        assertTrue("TELEMARKETING" in names)
        assertTrue("BANK_IMPERSONATION" in names)
        assertTrue("DELIVERY_SCAM" in names)
        assertTrue("TECH_SUPPORT_SCAM" in names)
        assertTrue("GOVERNMENT_IMPERSONATION" in names)
        assertTrue("HARASSMENT" in names)
        assertFalse("FRAUD_CONFIRMED" in names)
        assertFalse("IDENTITY_VERIFIED" in names)
    }

    @Test fun everyCategoryHasABoundedFrenchLabel() {
        CommunityReportClient.Category.entries.forEach { category ->
            assertTrue(category.frenchLabel.isNotBlank())
            assertTrue(category.frenchLabel.length <= 64)
        }
    }

    @Test fun publicEndpointDoesNotEmbedServerCredentialInItsUrl() {
        val endpoint = CommunityReportClient.ENDPOINT
        assertTrue(endpoint.startsWith("https://"))
        assertFalse(endpoint.contains("api_key", ignoreCase = true))
        assertFalse(endpoint.contains("token", ignoreCase = true))
        assertFalse(endpoint.contains("secret", ignoreCase = true))
    }
}
