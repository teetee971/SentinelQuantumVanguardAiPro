package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallRuleEngineTest {
    @Test fun explicitExactRuleBlocks() {
        val normalized = "+33612345678"
        val fingerprint = "a".repeat(64)
        val engine = CallRuleEngine(setOf(fingerprint), fingerprintNumber = { fingerprint })
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate(normalized).action)
    }

    @Test fun unavailableDeviceFingerprintFailsOpen() {
        val engine = CallRuleEngine(setOf("a".repeat(64)), fingerprintNumber = { null })
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("+33612345678").action)
    }

    @Test fun explicitPrefixRuleBlocks() {
        val engine = CallRuleEngine(blockedPrefixes = setOf("+33948"))
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("+33 9 48 12 34 56").action)
    }

    @Test fun bundledRuleSilencesButDoesNotBlock() {
        val result = CallRuleEngine().evaluate("08 99 12 34 56")
        assertEquals(CallRuleEngine.Action.SILENCE, result.action)
        assertEquals(CallRuleEngine.RuleSource.BUNDLED, result.source)
    }

    @Test fun unknownValidNumberIsAllowed() {
        assertEquals(CallRuleEngine.Action.ALLOW, CallRuleEngine().evaluate("+33 6 12 34 56 78").action)
    }

    @Test fun malformedOrUnavailableNumberFailsOpen() {
        val malformed = CallRuleEngine().evaluate("+33<script>")
        assertEquals(CallRuleEngine.Action.ALLOW, malformed.action)
        assertNull(malformed.normalizedNumber)
        assertEquals(CallRuleEngine.Action.ALLOW, CallRuleEngine().evaluate(null).action)
    }

    @Test fun corruptedPersistedRulesAreIgnored() {
        val engine = CallRuleEngine(setOf("not-a-hash"), setOf("+", "bad"))
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("+33 6 12 34 56 78").action)
    }
}
