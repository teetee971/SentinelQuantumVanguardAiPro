package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRiskCardTest {
    @Test fun missingEvidenceStaysUnknown() {
        val card = PhoneRiskCard.build(PhoneRiskCard.Input(riskScore = null), nowMs = 1_000L)
        assertEquals(PhoneRiskCard.RiskBand.UNKNOWN, card.riskBand)
        assertNull(card.riskScore)
        assertTrue(card.categories.isEmpty())
        assertTrue(card.reasons.isEmpty())
    }

    @Test fun invalidScoreDoesNotBecomeMaximumRisk() {
        val card = PhoneRiskCard.build(PhoneRiskCard.Input(riskScore = 140), nowMs = 1_000L)
        assertEquals(PhoneRiskCard.RiskBand.UNKNOWN, card.riskBand)
        assertNull(card.riskScore)
    }

    @Test fun staleReputationIsNotPresentedAsCurrentRisk() {
        val card = PhoneRiskCard.build(
            PhoneRiskCard.Input(
                riskScore = 92,
                flags = listOf("WANGIRI"),
                observedAtMs = 1_000L,
                ttlMs = 500L
            ),
            nowMs = 2_000L
        )
        assertEquals(PhoneRiskCard.Freshness.STALE, card.freshness)
        assertEquals(PhoneRiskCard.RiskBand.UNKNOWN, card.riskBand)
        assertNull(card.riskScore)
        assertEquals(listOf(PhoneFraudCategory.WANGIRI), card.categories)
        assertTrue(card.reasons.contains("Données de réputation expirées"))
    }

    @Test fun freshExplicitScoreMapsToDisplayBandOnly() {
        val card = PhoneRiskCard.build(
            PhoneRiskCard.Input(
                riskScore = 78,
                communitySignals = 12,
                flags = listOf("ROBOCALL", "ROBOCALL", "FAKE_BANK"),
                sourceLabel = "Sentinel Reputation",
                observedAtMs = 9_000L,
                ttlMs = 5_000L
            ),
            nowMs = 10_000L
        )
        assertEquals(PhoneRiskCard.Freshness.FRESH, card.freshness)
        assertEquals(PhoneRiskCard.RiskBand.MODERATE, card.riskBand)
        assertEquals(78, card.riskScore)
        assertEquals(12, card.communitySignals)
        assertEquals(
            listOf(PhoneFraudCategory.ROBOCALL, PhoneFraudCategory.BANK_IMPERSONATION),
            card.categories
        )
    }

    @Test fun riskBandsMirrorBackendDecisionBoundaries() {
        assertEquals(
            PhoneRiskCard.RiskBand.LOW,
            PhoneRiskCard.build(PhoneRiskCard.Input(riskScore = 49), 1L).riskBand
        )
        assertEquals(
            PhoneRiskCard.RiskBand.MODERATE,
            PhoneRiskCard.build(PhoneRiskCard.Input(riskScore = 50), 1L).riskBand
        )
        assertEquals(
            PhoneRiskCard.RiskBand.MODERATE,
            PhoneRiskCard.build(PhoneRiskCard.Input(riskScore = 79), 1L).riskBand
        )
        assertEquals(
            PhoneRiskCard.RiskBand.HIGH,
            PhoneRiskCard.build(PhoneRiskCard.Input(riskScore = 80), 1L).riskBand
        )
    }

    @Test fun scoreWithoutFreshnessMetadataRemainsExplicitlyUnmeasured() {
        val card = PhoneRiskCard.build(
            PhoneRiskCard.Input(riskScore = 20, sourceLabel = "remote"),
            nowMs = 10_000L
        )
        assertEquals(PhoneRiskCard.Freshness.UNKNOWN, card.freshness)
        assertEquals(PhoneRiskCard.RiskBand.LOW, card.riskBand)
        assertTrue(card.reasons.contains("Fraîcheur des données non déterminée"))
    }
}
