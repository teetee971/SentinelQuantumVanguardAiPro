package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallRuleEngineTest {
    @Test fun explicitExactRuleBlocks() {
        val normalized = "+33612345678"
        val fingerprint = "a".repeat(64)
        val engine = CallRuleEngine(setOf(fingerprint), fingerprintsForNumber = { setOf(fingerprint) })
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate(normalized).action)
    }

    @Test fun frenchNationalAndInternationalFormsMatchTheSameExactRule() {
        val blocked = "a".repeat(64)
        val fingerprint: (String) -> Set<String> = { setOf(if (it == "0612345678") blocked else "b".repeat(64)) }
        val engine = CallRuleEngine(setOf(blocked), fingerprintsForNumber = fingerprint)
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("+33 6 12 34 56 78").action)
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("0033 6 12 34 56 78").action)
    }

    @Test fun unavailableDeviceFingerprintFailsOpen() {
        val engine = CallRuleEngine(setOf("a".repeat(64)), fingerprintsForNumber = { emptySet() })
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("+33612345678").action)
    }

    @Test fun explicitPrefixRuleBlocks() {
        val engine = CallRuleEngine(blockedPrefixes = setOf("+33948"))
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("+33 9 48 12 34 56").action)
    }

    @Test fun signedReputationRuleSilencesButDoesNotBlock() {
        val result = CallRuleEngine(reputationSilencePrefixes = setOf("0899"))
            .evaluate("08 99 12 34 56")
        assertEquals(CallRuleEngine.Action.SILENCE, result.action)
        assertEquals(CallRuleEngine.RuleSource.SIGNED_REPUTATION, result.source)
    }

    @Test fun unsignedDefaultReputationIsEmpty() {
        assertEquals(CallRuleEngine.Action.ALLOW, CallRuleEngine().evaluate("08 99 12 34 56").action)
    }

    @Test fun frenchAndInternationalPrefixesCanonicalizeConsistently() {
        assertEquals("+33899", CallRuleEngine.normalizePrefix("0899"))
        assertEquals("+33899", CallRuleEngine.normalizePrefix("0033 899"))
        assertEquals("+33612345678", CallRuleEngine.normalizeNumber("06 12 34 56 78"))
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

    @Test fun versionedAndLegacyFingerprintsCanCoexistDuringRotation() {
        val legacy = "a".repeat(64)
        val active = "v2:${"b".repeat(64)}"
        val candidates = setOf(legacy, "v1:$legacy", active)
        assertEquals(
            CallRuleEngine.Action.BLOCK,
            CallRuleEngine(setOf(active), fingerprintsForNumber = { candidates }).evaluate("+33612345678").action
        )
        assertEquals(
            CallRuleEngine.Action.BLOCK,
            CallRuleEngine(setOf(legacy), fingerprintsForNumber = { candidates }).evaluate("+33612345678").action
        )
    }
}
