package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerReputationClientTest {

    @Test
    fun parsesCommunityEvidenceFromRuntimePayload() {
        val result = CallerReputationClient.parseResponse(
            """
            {
              "risk_score": 65,
              "action": "FLAG_SUSPICIOUS",
              "flags": ["Réputation communautaire dégradée"],
              "signals": 12,
              "caller_country": "DE",
              "is_international": true,
              "community_intelligence": "available",
              "warning": "Signal indicatif"
            }
            """.trimIndent()
        )

        assertEquals(65, result.riskScore)
        assertEquals("FLAG_SUSPICIOUS", result.action)
        assertEquals(12, result.signals)
        assertEquals("DE", result.callerCountry)
        assertTrue(result.isInternational == true)
        assertEquals("available", result.communityIntelligence)
        assertEquals(1, result.flags.size)
    }

    @Test
    fun boundsUntrustedRuntimeFields() {
        val result = CallerReputationClient.parseResponse(
            """
            {
              "risk_score": 999,
              "action": "ALLOW",
              "signals": -5,
              "caller_country": null,
              "is_international": false,
              "community_intelligence": "degraded",
              "warning": ""
            }
            """.trimIndent()
        )

        assertEquals(100, result.riskScore)
        assertEquals(0, result.signals)
        assertEquals(null, result.callerCountry)
        assertFalse(result.isInternational == true)
    }
}
