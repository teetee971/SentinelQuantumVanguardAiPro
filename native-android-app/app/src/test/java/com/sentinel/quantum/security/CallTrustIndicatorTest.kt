package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class CallTrustIndicatorTest {
    @Test
    fun explicitLocalBlockProducesLowConfidence() {
        val result = CallTrustIndicator.assess(
            CallTrustIndicator.Input(
                contactKnown = true,
                localDecision = CallRuleEngine.Decision(
                    action = CallRuleEngine.Action.BLOCK,
                    reason = "USER_EXACT_BLOCK",
                    normalizedNumber = "+33600000000",
                    source = CallRuleEngine.RuleSource.USER
                ),
                premiumRateCaution = false
            )
        )

        assertEquals(CallTrustIndicator.Level.HIGH_RISK, result.level)
        assertEquals("Confiance faible", result.title)
    }

    @Test
    fun signedSilenceRuleProducesCaution() {
        val result = CallTrustIndicator.assess(
            CallTrustIndicator.Input(
                contactKnown = false,
                localDecision = CallRuleEngine.Decision(
                    action = CallRuleEngine.Action.SILENCE,
                    reason = "SIGNED_REPUTATION_PREFIX",
                    normalizedNumber = "+33600000000",
                    source = CallRuleEngine.RuleSource.SIGNED_REPUTATION
                ),
                premiumRateCaution = false
            )
        )

        assertEquals(CallTrustIndicator.Level.CAUTION, result.level)
    }

    @Test
    fun savedContactIsOnlyIndicative() {
        val result = CallTrustIndicator.assess(
            CallTrustIndicator.Input(
                contactKnown = true,
                localDecision = CallRuleEngine.Decision(
                    action = CallRuleEngine.Action.ALLOW,
                    reason = "NO_MATCHING_RULE",
                    normalizedNumber = "+33600000000",
                    source = CallRuleEngine.RuleSource.NONE
                ),
                premiumRateCaution = false
            )
        )

        assertEquals(CallTrustIndicator.Level.INDICATIVE, result.level)
        assertEquals("Confiance indicative", result.title)
    }

    @Test
    fun noEvidenceRemainsUnknown() {
        val result = CallTrustIndicator.assess(
            CallTrustIndicator.Input(
                contactKnown = false,
                localDecision = CallRuleEngine.Decision(
                    action = CallRuleEngine.Action.ALLOW,
                    reason = "NO_MATCHING_RULE",
                    normalizedNumber = "+33600000000",
                    source = CallRuleEngine.RuleSource.NONE
                ),
                premiumRateCaution = false
            )
        )

        assertEquals(CallTrustIndicator.Level.UNKNOWN, result.level)
        assertEquals("Confiance non mesurée", result.title)
    }

    @Test
    fun premiumRatePrefixRaisesCautionWithoutClaimingFraud() {
        val result = CallTrustIndicator.assess(
            CallTrustIndicator.Input(
                contactKnown = false,
                localDecision = null,
                premiumRateCaution = true
            )
        )

        assertEquals(CallTrustIndicator.Level.CAUTION, result.level)
        assertEquals("Vigilance renforcée", result.title)
    }
}
