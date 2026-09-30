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

    @Test fun validRiskScoreIsPreserved() {
        val result = CallerReputationClient.parseResponse(
            """{"risk_score":78,"signals":12,"flags":["ROBOCALL"]}"""
        )
        assertEquals(78, result.riskScore)
        assertEquals(12, result.signals)
        assertEquals(listOf("ROBOCALL"), result.flags)
    }
}
