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

    @Test fun explicitInternationalFormsMatchTheSameExactRule() {
        val blocked = "a".repeat(64)
        val fingerprint: (String) -> Set<String> = { value ->
            setOf(if (value == "+33612345678" || value == "0033612345678") blocked else "b".repeat(64))
        }
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

    @Test fun regionlessNationalFormsNeverInventFrance() {
        assertEquals("0899", CallRuleEngine.normalizePrefix("0899"))
        assertEquals("+33899", CallRuleEngine.normalizePrefix("0033 899"))
        assertEquals("0612345678", CallRuleEngine.normalizeNumber("06 12 34 56 78"))
        assertEquals("0590123456", CallRuleEngine.normalizeNumber("05 90 12 34 56"))
        assertEquals("0690123456", CallRuleEngine.normalizeNumber("06 90 12 34 56"))
    }

    @Test fun explicitInternationalCountryCallingCodesRemainStable() {
        assertEquals("+590690123456", CallRuleEngine.normalizeNumber("+590 690 12 34 56"))
        assertEquals("+590690123456", CallRuleEngine.normalizeNumber("00590 690 12 34 56"))
        assertEquals("+594694123456", CallRuleEngine.normalizeNumber("+594 694 12 34 56"))
        assertEquals("+596696123456", CallRuleEngine.normalizeNumber("+596 696 12 34 56"))
        assertEquals("+262692123456", CallRuleEngine.normalizeNumber("+262 692 12 34 56"))
    }

    @Test fun nationalGuadeloupeNumberCannotFalseMatchFrenchPrefixWithoutRegion() {
        val engine = CallRuleEngine(blockedPrefixes = setOf("+33690"))
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("06 90 12 34 56").action)
    }

    @Test fun observedRegionCanBeInjectedWithoutChangingPureEnginePolicy() {
        val blocked = "a".repeat(64)
        val engine = CallRuleEngine(
            blockedNumberHashes = setOf(blocked),
            fingerprintsForNumber = { value ->
                setOf(if (value == "+590690123456") blocked else "b".repeat(64))
            },
            numberNormalizer = { raw ->
                when (CallRuleEngine.normalizeNumber(raw)) {
                    "0690123456" -> "+590690123456"
                    else -> CallRuleEngine.normalizeNumber(raw)
                }
            }
        )
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("06 90 12 34 56").action)
    }

    @Test fun internationalCountryCallingCodeCanBeBlockedExplicitly() {
        val engine = CallRuleEngine(blockedPrefixes = setOf("+590"))
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("+590690123456").action)
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("+33612345678").action)
    }

    @Test fun shortInternationalCountryPrefixNormalizesOnlyWhenExplicit() {
        assertEquals("+33", CallRuleEngine.normalizePrefix("+33"))
        assertEquals("+1", CallRuleEngine.normalizePrefix("+1"))
        assertEquals("+590", CallRuleEngine.normalizePrefix("00590"))
        assertNull(CallRuleEngine.normalizePrefix("33"))
        assertNull(CallRuleEngine.normalizePrefix("1"))
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
