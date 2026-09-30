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
            """{"risk_score":78,"signals":12,"flags":["Réputation communautaire dégradée"],"categories":["ROBOCALL","BANK_IMPERSONATION","ROBOCALL"]}"""
        )
        assertEquals(78, result.riskScore)
        assertEquals(12, result.signals)
        assertEquals(listOf("Réputation communautaire dégradée"), result.flags)
        assertEquals(listOf("ROBOCALL", "BANK_IMPERSONATION"), result.categories)
    }
}
