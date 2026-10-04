package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

    @Test fun publicEndpointIsCredentialFreeAndPinnedToInjectedHost() {
        val endpoint = CommunityReportClient.endpoint(
            "https://api.example.test",
            setOf("api.example.test")
        )

        assertEquals("https", endpoint.scheme)
        assertEquals("api.example.test", endpoint.host)
        assertEquals("/v1/report-call-public", endpoint.encodedPath)
        assertFalse(endpoint.toString().contains("api_key", ignoreCase = true))
        assertFalse(endpoint.toString().contains("token", ignoreCase = true))
        assertFalse(endpoint.toString().contains("secret", ignoreCase = true))
    }

    @Test fun publicEndpointRejectsHostMismatch() {
        assertThrows(SecurityException::class.java) {
            CommunityReportClient.endpoint(
                "https://evil.example.test",
                setOf("api.example.test")
            )
        }
    }
}
