package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectiveDefenseClientTest {
    @Test
    fun parsesBoundedReputationResponse() {
        val result = CollectiveDefenseClient.parseReputation(
            """
            {
              "indicator_type":"DOMAIN",
              "indicator_fingerprint":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "risk_state":"SUSPICIOUS",
              "signals":4,
              "categories":["PHISHING","CREDENTIAL_THEFT","PHISHING"],
              "community_intelligence":"available",
              "reputation_observed_at_ms":123000,
              "reputation_ttl_ms":3600000,
              "enforcement_allowed":false,
              "warning":"Signal technique uniquement."
            }
            """.trimIndent()
        )

        assertEquals(CollectiveDefenseClient.IndicatorType.DOMAIN, result.indicatorType)
        assertEquals("a".repeat(64), result.indicatorFingerprint)
        assertEquals("SUSPICIOUS", result.riskState)
        assertEquals(4, result.signals)
        assertEquals(listOf("PHISHING", "CREDENTIAL_THEFT"), result.categories)
        assertEquals("available", result.communityIntelligence)
        assertEquals(123000L, result.reputationObservedAtMs)
        assertEquals(3600000L, result.reputationTtlMs)
        assertFalse(result.enforcementAllowed)
    }

    @Test
    fun rejectsInvalidFingerprintInServerResponse() {
        assertThrows(IllegalStateException::class.java) {
            CollectiveDefenseClient.parseReputation(
                """
                {
                  "indicator_type":"URL",
                  "indicator_fingerprint":"bad",
                  "risk_state":"UNKNOWN",
                  "signals":0,
                  "categories":[],
                  "community_intelligence":"available",
                  "enforcement_allowed":false
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun rejectsBackendAttemptToEnableAutomaticEnforcement() {
        assertThrows(SecurityException::class.java) {
            CollectiveDefenseClient.parseReputation(
                """
                {
                  "indicator_type":"DOMAIN",
                  "indicator_fingerprint":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "risk_state":"SUSPICIOUS",
                  "signals":4,
                  "categories":["PHISHING"],
                  "community_intelligence":"available",
                  "enforcement_allowed":true
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun recheckMustMatchRequestedTypeAndFingerprint() {
        val result = CollectiveDefenseClient.ReputationResult(
            indicatorType = CollectiveDefenseClient.IndicatorType.EMAIL,
            indicatorFingerprint = "b".repeat(64),
            riskState = "OBSERVED",
            signals = 1,
            categories = listOf("PHISHING"),
            communityIntelligence = "available",
            reputationObservedAtMs = 123L,
            reputationTtlMs = 456L,
            enforcementAllowed = false,
            warning = ""
        )

        assertThrows(SecurityException::class.java) {
            CollectiveDefenseClient.requireExpectedResult(
                CollectiveDefenseClient.IndicatorType.DOMAIN,
                "b".repeat(64),
                result
            )
        }
        assertThrows(SecurityException::class.java) {
            CollectiveDefenseClient.requireExpectedResult(
                CollectiveDefenseClient.IndicatorType.EMAIL,
                "c".repeat(64),
                result
            )
        }
        assertEquals(
            result,
            CollectiveDefenseClient.requireExpectedResult(
                CollectiveDefenseClient.IndicatorType.EMAIL,
                "b".repeat(64),
                result
            )
        )
    }

    @Test
    fun endpointPolicyAllowsOnlyPinnedHttpsIntelligenceRoutes() {
        val allowed = setOf("api.example.test")
        val url = CollectiveDefenseClient.buildEndpoint(
            "https://api.example.test",
            "/v1/intelligence/lookup",
            allowed
        )
        assertEquals("https", url.scheme)
        assertEquals("api.example.test", url.host)
        assertEquals("/v1/intelligence/lookup", url.encodedPath)

        assertThrows(SecurityException::class.java) {
            CollectiveDefenseClient.buildEndpoint(
                "http://api.example.test",
                "/v1/intelligence/lookup",
                allowed
            )
        }
        assertThrows(SecurityException::class.java) {
            CollectiveDefenseClient.buildEndpoint(
                "https://evil.example",
                "/v1/intelligence/lookup",
                allowed
            )
        }
        assertThrows(SecurityException::class.java) {
            CollectiveDefenseClient.buildEndpoint(
                "https://api.example.test",
                "/health/ready",
                allowed
            )
        }
    }

    @Test
    fun disabledIntelligenceMayOmitFingerprintWithoutCrashing() {
        val result = CollectiveDefenseClient.parseReputation(
            """
            {
              "indicator_type":"DOMAIN",
              "indicator_fingerprint":null,
              "risk_state":"UNKNOWN",
              "signals":0,
              "categories":[],
              "community_intelligence":"disabled",
              "enforcement_allowed":false
            }
            """.trimIndent()
        )
        assertEquals(null, result.indicatorFingerprint)
        assertEquals("disabled", result.communityIntelligence)
    }

    @Test
    fun availableIntelligenceMustProvideFingerprint() {
        assertThrows(IllegalStateException::class.java) {
            CollectiveDefenseClient.parseReputation(
                """
                {
                  "indicator_type":"DOMAIN",
                  "indicator_fingerprint":null,
                  "risk_state":"UNKNOWN",
                  "signals":0,
                  "categories":[],
                  "community_intelligence":"available",
                  "enforcement_allowed":false
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun rejectsNonStandardHttpsPortAndOversizedRawIndicator() {
        val allowed = setOf("api.example.test")
        assertThrows(SecurityException::class.java) {
            CollectiveDefenseClient.buildEndpoint(
                "https://api.example.test:8443",
                "/v1/intelligence/lookup",
                allowed
            )
        }

        val client = CollectiveDefenseClient()
        assertThrows(IllegalArgumentException::class.java) {
            client.lookup(
                CollectiveDefenseClient.IndicatorType.URL,
                "x".repeat(4097)
            )
        }
    }

    @Test
    fun negativeOrMissingFreshnessDoesNotBecomeValidFreshness() {
        val result = CollectiveDefenseClient.parseReputation(
            """
            {
              "indicator_type":"SHA256",
              "indicator_fingerprint":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
              "risk_state":"UNKNOWN",
              "signals":0,
              "categories":[],
              "community_intelligence":"disabled",
              "reputation_observed_at_ms":-1,
              "reputation_ttl_ms":-1,
              "enforcement_allowed":false
            }
            """.trimIndent()
        )
        assertEquals(null, result.reputationObservedAtMs)
        assertEquals(null, result.reputationTtlMs)
        assertTrue(result.categories.isEmpty())
    }
}
