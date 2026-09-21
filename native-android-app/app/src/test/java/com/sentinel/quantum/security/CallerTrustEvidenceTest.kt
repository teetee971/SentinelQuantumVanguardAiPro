package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerTrustEvidenceTest {
    @Test fun noEvidenceIsNotPresentedAsSafe() {
        val result = CallerTrustEvidence.assess(emptyList())
        assertEquals(0, result.riskScore)
        assertEquals(0, result.confidenceScore)
        assertTrue(result.explanation.contains("insuffisantes"))
    }

    @Test fun confidenceTracksIndependentSourcesNotRisk() {
        val result = CallerTrustEvidence.assess(listOf(
            CallerTrustEvidence.Evidence(CallerTrustEvidence.Source.NETWORK, CallerTrustEvidence.Kind.IDENTITY, "verified", "Validation réseau", 0),
            CallerTrustEvidence.Evidence(CallerTrustEvidence.Source.COMMUNITY, CallerTrustEvidence.Kind.RISK, "wangiri", "Signalements Wangiri", 80)
        ))
        assertEquals(80, result.riskScore)
        assertTrue(result.confidenceScore > 0)
    }

    @Test fun localTrustCanReduceButNotEraseRiskSemantics() {
        val result = CallerTrustEvidence.assess(listOf(
            CallerTrustEvidence.Evidence(CallerTrustEvidence.Source.COMMUNITY, CallerTrustEvidence.Kind.RISK, "reports", "Signalements récents", 80),
            CallerTrustEvidence.Evidence(CallerTrustEvidence.Source.CONTACTS, CallerTrustEvidence.Kind.TRUST, "contact", "Contact local", 40)
        ))
        assertEquals(60, result.riskScore)
    }
    @Test fun expiredEvidenceCannotInfluenceAssessment() {
        val now = 2_000L
        val result = CallerTrustEvidence.assess(listOf(
            CallerTrustEvidence.Evidence(
                CallerTrustEvidence.Source.COMMUNITY,
                CallerTrustEvidence.Kind.RISK,
                "stale",
                "Ancien signalement",
                100,
                observedAtEpochMs = 500L,
                expiresAtEpochMs = 1_000L
            )
        ), nowEpochMs = now)
        assertEquals(0, result.riskScore)
        assertEquals(0, result.confidenceScore)
    }

}
