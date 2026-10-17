package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.CallRuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CallDecisionEvidenceAdapterTest {
    @Test
    fun maps_call_rule_decision_without_exporting_number() {
        val rawNumber = "+33612345678"
        val decision = CallRuleEngine.Decision(
            action = CallRuleEngine.Action.BLOCK,
            reason = "USER_EXACT_BLOCK",
            normalizedNumber = rawNumber,
            source = CallRuleEngine.RuleSource.USER
        )

        val evidence = CallDecisionEvidenceAdapter.from(decision)

        assertEquals(IntelligenceEvidence.CallAction.BLOCK, evidence.action)
        assertEquals("USER", evidence.ruleSource)
        assertEquals("USER_EXACT_BLOCK", evidence.reasonCode)
        assertEquals(IntelligenceEvidence.NetworkVerification.NOT_VERIFIED, evidence.networkVerification)
        assertFalse(evidence.toString().contains(rawNumber))
        assertFalse(evidence.toString().contains("0612345678"))
    }

    @Test
    fun signed_reputation_rule_remains_silence_not_block() {
        val decision = CallRuleEngine.Decision(
            action = CallRuleEngine.Action.SILENCE,
            reason = "SIGNED_REPUTATION_PREFIX",
            normalizedNumber = "+33123456789",
            source = CallRuleEngine.RuleSource.SIGNED_REPUTATION
        )

        val evidence = CallDecisionEvidenceAdapter.from(decision)

        assertEquals(IntelligenceEvidence.CallAction.SILENCE, evidence.action)
        assertEquals("SIGNED_REPUTATION", evidence.ruleSource)
    }
}
