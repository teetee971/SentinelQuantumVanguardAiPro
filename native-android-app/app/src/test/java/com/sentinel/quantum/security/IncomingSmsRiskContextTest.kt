package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingSmsRiskContextTest {
    @Test fun freshHighCachedReputationRaisesLowSmsContext() {
        val storage = InMemoryStorage()
        val cache = CommunicationReputationCache(storage)
        val sender = "+33612345678"
        assertTrue(
            cache.put(
                sender,
                CommunicationReputationCache.Entry(
                    riskScore = 91,
                    categories = listOf("SPOOFING"),
                    signals = 4,
                    observedAtMs = 900L,
                    ttlMs = 5_000L
                ),
                nowMs = 1_000L
            )
        )

        val assessment = IncomingSmsRiskContext(cache).assess(
            sender = sender,
            sms = smsAnalysis(SmsLinkAnalyzer.RiskLevel.LOW),
            nowMs = 1_000L
        )

        assertEquals(UnifiedCommunicationRisk.Level.HIGH, assessment.level)
        assertEquals(PhoneRiskCard.RiskBand.HIGH, assessment.reputationBand)
        assertTrue(assessment.reasons.contains(UnifiedCommunicationRisk.Reason.REPUTATION_HIGH))
    }

    @Test fun expiredReputationCannotInfluenceIncomingSms() {
        val storage = InMemoryStorage()
        val cache = CommunicationReputationCache(storage)
        val sender = "+33612345678"
        assertTrue(
            cache.put(
                sender,
                CommunicationReputationCache.Entry(
                    riskScore = 95,
                    categories = emptyList(),
                    signals = 8,
                    observedAtMs = 1_000L,
                    ttlMs = 100L
                ),
                nowMs = 1_000L
            )
        )

        val assessment = IncomingSmsRiskContext(cache).assess(
            sender = sender,
            sms = smsAnalysis(SmsLinkAnalyzer.RiskLevel.LOW),
            nowMs = 1_500L
        )

        assertEquals(UnifiedCommunicationRisk.Level.LOW, assessment.level)
        assertEquals(PhoneRiskCard.RiskBand.UNKNOWN, assessment.reputationBand)
    }

    @Test fun revokedConsentMakesCachedReputationInvisible() {
        val storage = InMemoryStorage()
        val cache = CommunicationReputationCache(storage)
        val sender = "+33612345678"
        assertTrue(
            cache.put(
                sender,
                CommunicationReputationCache.Entry(
                    riskScore = 99,
                    categories = listOf("SPOOFING"),
                    signals = 20,
                    observedAtMs = 900L,
                    ttlMs = 5_000L
                ),
                nowMs = 1_000L
            )
        )

        val assessment = IncomingSmsRiskContext(cache).assess(
            sender = sender,
            sms = smsAnalysis(SmsLinkAnalyzer.RiskLevel.LOW),
            allowCachedReputation = false,
            nowMs = 1_000L
        )

        assertEquals(UnifiedCommunicationRisk.Level.LOW, assessment.level)
        assertEquals(PhoneRiskCard.RiskBand.UNKNOWN, assessment.reputationBand)
        assertTrue(assessment.reasons.isEmpty())
    }

    private fun smsAnalysis(level: SmsLinkAnalyzer.RiskLevel) =
        SmsLinkAnalyzer.Analysis(
            accepted = true,
            reason = null,
            riskLevel = level,
            score = 0,
            findings = emptyList(),
            analyzedAt = 1_000L,
            linksInspected = 0
        )

    private class InMemoryStorage : CommunicationReputationCache.Storage {
        private val values = linkedMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
}
