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

    @Test fun guadeloupeNationalNumberUses590NotMetropolitan33() {
        assertEquals("+590690123456", CallRuleEngine.normalizeNumber("0690 12 34 56"))
        assertEquals("+590690123456", CallRuleEngine.normalizeNumber("+590 690 12 34 56"))
        assertEquals("+33612345678", CallRuleEngine.normalizeNumber("06 12 34 56 78"))
    }

    @Test fun guadeloupeNationalAndE164MatchTheSameExactRule() {
        val blocked = "a".repeat(64)
        val fingerprint: (String) -> Set<String> = {
            setOf(if (it == "0690123456") blocked else "b".repeat(64))
        }
        val engine = CallRuleEngine(setOf(blocked), fingerprintsForNumber = fingerprint)
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("+590690123456").action)
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("0690123456").action)
    }

    @Test fun numberOutsideDirectoryIsSilencedOnlyWhenDirectoryIsReady() {
        val known = CallRuleEngine(
            silenceOutsideDirectory = true,
            directoryReady = true,
            directoryContains = { it == "+590690123456" }
        )
        assertEquals(CallRuleEngine.Action.ALLOW, known.evaluate("0690 12 34 56").action)
        val unknown = CallRuleEngine(
            silenceOutsideDirectory = true,
            directoryReady = true,
            directoryContains = { false }
        )
        val silenced = unknown.evaluate("+33 6 12 34 56 78")
        assertEquals(CallRuleEngine.Action.SILENCE, silenced.action)
        assertEquals("NOT_IN_DIRECTORY", silenced.reason)
        assertEquals(
            CallRuleEngine.Action.ALLOW,
            CallRuleEngine(silenceOutsideDirectory = true, directoryReady = false).evaluate("+33612345678").action
        )
    }

    @Test fun withheldOrUnavailableNumberIsNotSilenced() {
        val armed = CallRuleEngine(silenceOutsideDirectory = true, directoryReady = true)
        assertEquals(CallRuleEngine.Action.ALLOW, armed.evaluate(null).action)
        assertEquals(CallRuleEngine.Action.ALLOW, CallRuleEngine().evaluate(null).action)
    }

    @Test fun contactRingsEvenWhenAReputationPrefixWouldSilence() {
        val engine = CallRuleEngine(
            reputationSilencePrefixes = setOf("0899"),
            silenceOutsideDirectory = true,
            directoryReady = true,
            directoryContains = { it == "+33899123456" }
        )
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("0899 12 34 56").action)
    }

    @Test fun explicitBlockStillWinsOverDirectorySilence() {
        val fingerprint = "a".repeat(64)
        val engine = CallRuleEngine(
            blockedNumberHashes = setOf(fingerprint),
            fingerprintsForNumber = { setOf(fingerprint) },
            silenceOutsideDirectory = true,
            directoryReady = true,
            directoryContains = { false }
        )
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("+33612345678").action)
    }
}
