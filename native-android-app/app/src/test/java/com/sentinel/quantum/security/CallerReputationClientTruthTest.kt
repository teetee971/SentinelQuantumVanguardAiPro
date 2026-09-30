package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerReputationClientTruthTest {
    @Test fun missingRiskScoreStaysUnknown() {
        val result = CallerReputationClient.parseResponse("{}")
        assertNull(result.riskScore)
    }

    @Test fun explicitZeroRiskScoreRemainsMeasuredZero() {
        val result = CallerReputationClient.parseResponse("""{"risk_score":0}""")
        assertEquals(0, result.riskScore)
    }

    @Test fun outOfRangeRiskScoreIsRejectedInsteadOfClamped() {
        val result = CallerReputationClient.parseResponse("""{"risk_score":140}""")
        assertNull(result.riskScore)
    }

    @Test fun missingCategoriesStayEmpty() {
        val result = CallerReputationClient.parseResponse("""{"risk_score":50}""")
        assertEquals(emptyList<String>(), result.categories)
    }

    @Test fun validRiskScoreIsPreserved() {
        val result = CallerReputationClient.parseResponse(
            """{"risk_score":78,"signals":12,"flags":["Réputation communautaire dégradée"],"categories":["ROBOCALL","BANK_IMPERSONATION","ROBOCALL"],"reputation_observed_at_ms":1700000000000,"reputation_ttl_ms":15552000000}"""
        )
        assertEquals(78, result.riskScore)
        assertEquals(12, result.signals)
        assertEquals(listOf("Réputation communautaire dégradée"), result.flags)
        assertEquals(listOf("ROBOCALL", "BANK_IMPERSONATION"), result.categories)
        assertEquals(1_700_000_000_000L, result.reputationObservedAtMs)
        assertEquals(15_552_000_000L, result.reputationTtlMs)
    }

    @Test fun missingOrInvalidFreshnessStaysUnknown() {
        val missing = CallerReputationClient.parseResponse("""{"risk_score":50}""")
        assertNull(missing.reputationObservedAtMs)
        assertNull(missing.reputationTtlMs)

        val invalid = CallerReputationClient.parseResponse(
            """{"risk_score":50,"reputation_observed_at_ms":-1,"reputation_ttl_ms":0}"""
        )
        assertNull(invalid.reputationObservedAtMs)
        assertNull(invalid.reputationTtlMs)
    }
}
