package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallRuleBoundaryTest {
    @Test fun signedReputationCanSilenceButNeverBlock() {
        val engine = CallRuleEngine(reputationSilencePrefixes = setOf("+33612"))
        val decision = engine.evaluate("+33612345678")
        assertEquals(CallRuleEngine.Action.SILENCE, decision.action)
        assertEquals(CallRuleEngine.RuleSource.SIGNED_REPUTATION, decision.source)
    }

    @Test fun explicitUserPrefixCanBlock() {
        val engine = CallRuleEngine(blockedPrefixes = setOf("+33612"))
        val decision = engine.evaluate("+33612345678")
        assertEquals(CallRuleEngine.Action.BLOCK, decision.action)
        assertEquals(CallRuleEngine.RuleSource.USER, decision.source)
    }

    @Test fun malformedCallerIdFailsOpen() {
        val decision = CallRuleEngine().evaluate("javascript:alert(1)")
        assertEquals(CallRuleEngine.Action.ALLOW, decision.action)
        assertEquals("INVALID_OR_UNAVAILABLE_NUMBER", decision.reason)
        assertNull(decision.normalizedNumber)
    }

    @Test fun internationalAndFrenchRepresentationsRemainEquivalentForMatching() {
        val forms = CallRuleEngine.matchingRepresentations("+33612345678")
        assertEquals(setOf("+33612345678", "0612345678", "0033612345678"), forms)
    }
}
