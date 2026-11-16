package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedCommunicationRiskTest {
    @Test fun missingEvidenceStaysUnknown() {
        val assessment = UnifiedCommunicationRisk.assess(sms = null, reputation = null)

        assertEquals(UnifiedCommunicationRisk.Level.UNKNOWN, assessment.level)
        assertEquals(SmsLinkAnalyzer.RiskLevel.UNKNOWN, assessment.smsLevel)
        assertEquals(PhoneRiskCard.RiskBand.UNKNOWN, assessment.reputationBand)
        assertTrue(assessment.reasons.isEmpty())
    }

    @Test fun highSmsRiskCannotBeLoweredByLowReputation() {
        val assessment = UnifiedCommunicationRisk.assess(
            sms = smsAnalysis(SmsLinkAnalyzer.RiskLevel.HIGH, score = 60),
            reputation = PhoneRiskCard.build(PhoneRiskCard.Input(riskScore = 10), nowMs = 1_000L)
        )

        assertEquals(UnifiedCommunicationRisk.Level.HIGH, assessment.level)
        assertTrue(assessment.reasons.contains(UnifiedCommunicationRisk.Reason.SMS_CONTENT_HIGH))
    }

    @Test fun highCachedReputationRaisesOtherwiseLowSmsContext() {
        val assessment = UnifiedCommunicationRisk.assess(
            sms = smsAnalysis(SmsLinkAnalyzer.RiskLevel.LOW, score = 0),
            reputation = PhoneRiskCard.build(
                PhoneRiskCard.Input(
                    riskScore = 90,
                    observedAtMs = 900L,
                    ttlMs = 1_000L
                ),
                nowMs = 1_000L
            )
        )

        assertEquals(UnifiedCommunicationRisk.Level.HIGH, assessment.level)
        assertEquals(SmsLinkAnalyzer.RiskLevel.LOW, assessment.smsLevel)
        assertEquals(PhoneRiskCard.RiskBand.HIGH, assessment.reputationBand)
        assertTrue(assessment.reasons.contains(UnifiedCommunicationRisk.Reason.REPUTATION_HIGH))
    }

    @Test fun moderateSignalsKeepIndependentReasons() {
        val assessment = UnifiedCommunicationRisk.assess(
            sms = smsAnalysis(SmsLinkAnalyzer.RiskLevel.MEDIUM, score = 25),
            reputation = PhoneRiskCard.build(
                PhoneRiskCard.Input(
                    riskScore = 60,
                    observedAtMs = 900L,
                    ttlMs = 1_000L
                ),
                nowMs = 1_000L
            )
        )

        assertEquals(UnifiedCommunicationRisk.Level.MODERATE, assessment.level)
        assertTrue(assessment.reasons.contains(UnifiedCommunicationRisk.Reason.SMS_CONTENT_MODERATE))
        assertTrue(assessment.reasons.contains(UnifiedCommunicationRisk.Reason.REPUTATION_MODERATE))
    }

    private fun smsAnalysis(level: SmsLinkAnalyzer.RiskLevel, score: Int) =
        SmsLinkAnalyzer.Analysis(
            accepted = true,
            reason = null,
            riskLevel = level,
            score = score,
            findings = emptyList(),
            analyzedAt = 1_000L,
            linksInspected = 0
        )
}
