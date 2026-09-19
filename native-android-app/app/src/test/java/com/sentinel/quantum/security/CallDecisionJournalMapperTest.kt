package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CallDecisionJournalMapperTest {
    @Test
    fun signedReputationSilenceMapsWithoutNumber() {
        val decision = CallRuleEngine.Decision(
            action = CallRuleEngine.Action.SILENCE,
            reason = "SIGNED_REPUTATION_PREFIX",
            normalizedNumber = "+33123456789",
            source = CallRuleEngine.RuleSource.SIGNED_REPUTATION
        )

        val entry = CallDecisionJournalMapper.toEntry(decision, 1234L)

        assertEquals(PhoneDecisionJournal.Action.SILENCE, entry.action)
        assertEquals(SentinelConfidence.CORROBORATED, entry.confidence)
        assertEquals("SIGNED_REPUTATION_PREFIX", entry.ruleCode)
        assertEquals("SIGNED_REPUTATION", entry.provenance)
        assertEquals(true, entry.localOnly)
        assertFalse(entry.toString().contains("+33123456789"))
    }

    @Test
    fun explicitUserBlockMapsAsVerifiedLocalDecision() {
        val decision = CallRuleEngine.Decision(
            action = CallRuleEngine.Action.BLOCK,
            reason = "USER_EXACT_BLOCK",
            normalizedNumber = "+590690000000",
            source = CallRuleEngine.RuleSource.USER
        )

        val entry = CallDecisionJournalMapper.toEntry(decision, 5678L)

        assertEquals(PhoneDecisionJournal.Action.BLOCK, entry.action)
        assertEquals(SentinelConfidence.VERIFIED, entry.confidence)
        assertEquals("USER", entry.provenance)
        assertEquals(true, entry.localOnly)
        assertFalse(entry.toString().contains("+590690000000"))
    }
}
