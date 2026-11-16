package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun overseasTerritoriesAreNotRewrittenAsMetropolitanFrance() {
        assertEquals("+590691123456", CallRuleEngine.normalizeNumber("0691123456"))
        assertEquals("+594694123456", CallRuleEngine.normalizeNumber("0694123456"))
        assertEquals("+596696123456", CallRuleEngine.normalizeNumber("0696123456"))
        assertEquals("+262692123456", CallRuleEngine.normalizeNumber("0692123456"))
        assertEquals("+262269123456", CallRuleEngine.normalizeNumber("0269123456"))
        assertEquals("+262639123456", CallRuleEngine.normalizeNumber("0639123456"))
        assertEquals("+590590123456", CallRuleEngine.normalizeNumber("0590123456"))
        assertEquals("+262262123456", CallRuleEngine.normalizeNumber("0262123456"))
        assertEquals("+33612345678", CallRuleEngine.normalizeNumber("+33612345678"))
        assertEquals("+590598712345", CallRuleEngine.normalizeNumber("0598712345"))
        assertEquals("+594598812345", CallRuleEngine.normalizeNumber("0598812345"))
        assertEquals("+596598912345", CallRuleEngine.normalizeNumber("0598912345"))
        assertEquals("+590947512345", CallRuleEngine.normalizeNumber("0947512345"))
        assertEquals("+590598501234", CallRuleEngine.normalizeNumber("0598501234"))
        assertEquals("+33598012345", CallRuleEngine.normalizeNumber("0598012345"))
        assertEquals("+33598651234", CallRuleEngine.normalizeNumber("0598651234"))
        assertEquals("+33947112345", CallRuleEngine.normalizeNumber("0947112345"))
        assertEquals("+508601234", CallRuleEngine.normalizeNumber("0508601234"))
        assertEquals("Polyvalent vérifié", CallRuleEngine.displayKind("0598712345"))
        assertEquals("Intérêt général", CallRuleEngine.displayKind("0151012345"))
        assertEquals("Intérêt général", CallRuleEngine.displayKind("0508601234"))
        assertEquals(CallRuleEngine.Action.ALLOW, CallRuleEngine().evaluate("0151012345").action)
        assertEquals(CallRuleEngine.Action.ALLOW, CallRuleEngine().evaluate("0598712345").action)
        assertEquals(
            CallRuleEngine.Action.BLOCK,
            CallRuleEngine(blockedPrefixes = setOf("+5905987")).evaluate("0598712345").action
        )
    }

    @Test fun everyCriticalOverseasRootKeepsItsCountryInNationalAndPlus33Form() {
        val roots = listOf(
            "0590" to "590", "0591" to "590", "0690" to "590", "0691" to "590",
            "059850" to "590", "059851" to "590", "059852" to "590", "059853" to "590", "059854" to "590",
            "05987" to "590", "094700" to "590", "094701" to "590", "09475" to "590",
            "09760" to "590", "09761" to "590", "09768" to "590",
            "0594" to "594", "0694" to "594",
            "059855" to "594", "059856" to "594", "059857" to "594", "059858" to "594", "059859" to "594",
            "05988" to "594", "094702" to "594", "094703" to "594", "09476" to "594",
            "09764" to "594", "09765" to "594",
            "0596" to "596", "0597" to "596", "0696" to "596",
            "059860" to "596", "059861" to "596", "059862" to "596", "059863" to "596", "059864" to "596",
            "05989" to "596", "094704" to "596", "094705" to "596", "09477" to "596",
            "09766" to "596", "09767" to "596",
            "0262" to "262", "0263" to "262", "0692" to "262",
            "026870" to "262", "026871" to "262", "026872" to "262", "026873" to "262", "026874" to "262",
            "02688" to "262", "094706" to "262", "094707" to "262", "09479" to "262",
            "09762" to "262", "09763" to "262",
            "0269" to "262", "0693" to "262", "0639" to "262",
            "026875" to "262", "026876" to "262", "026877" to "262", "026878" to "262", "026879" to "262",
            "02689" to "262", "094708" to "262", "094709" to "262", "09478" to "262",
            "09769" to "262", "09900" to "262",
            "0508" to "508"
        )
        assertEquals(72, roots.size)
        for ((prefix, countryCode) in roots) {
            val national = (prefix + "1234567890").take(10)
            val expected = if (prefix.startsWith("0508")) "+508" + national.drop(4) else "+$countryCode" + national.drop(1)
            assertEquals(prefix, expected, CallRuleEngine.normalizeNumber(national))
            assertEquals(prefix, expected, CallRuleEngine.normalizeNumber("+33" + national.drop(1)))
            assertEquals(prefix, expected, CallRuleEngine.normalizeNumber("0033" + national.drop(1)))
            val contact = CallRuleEngine(
                silenceOutsideDirectory = true,
                directoryReady = true,
                directoryContains = { it == expected }
            )
            assertEquals(prefix, CallRuleEngine.Action.ALLOW, contact.evaluate(national).action)
            assertEquals(
                prefix,
                CallRuleEngine.Action.BLOCK,
                CallRuleEngine(blockedPrefixes = setOf("+$countryCode")).evaluate("+33" + national.drop(1)).action
            )
        }
    }

    @Test fun unassignedReserveRootsStayMetropolitanAndDoNotSilence() {
        val reserves = listOf(
            "0264", "0592", "0593", "0595", "0599",
            "02680", "02681", "02682", "02683", "02684", "02685", "02686",
            "05980", "05981", "05982", "05983", "05984",
            "059865", "059866", "059867", "059868", "059869"
        )
        for (prefix in reserves) {
            val national = (prefix + "1234567890").take(10)
            assertEquals(prefix, "+33" + national.drop(1), CallRuleEngine.normalizeNumber(national))
            assertEquals(prefix, "Réserve, non attribué", CallRuleEngine.displayKind(national))
            assertEquals(prefix, CallRuleEngine.Action.ALLOW, CallRuleEngine().evaluate(national).action)
        }
        for (prefix in listOf("09471", "09472", "09473", "09474")) {
            val national = (prefix + "1234567890").take(10)
            assertEquals(prefix, "+33" + national.drop(1), CallRuleEngine.normalizeNumber(national))
            assertEquals(prefix, "Polyvalent / voix sur IP", CallRuleEngine.displayKind(national))
        }
    }

    @Test fun legacyPlus33OverseasNumberFoldsToTerritoryIdentity() {
        assertEquals("+590690123456", CallRuleEngine.normalizeNumber("+33 6 90 12 34 56"))
        assertEquals("+590690123456", CallRuleEngine.normalizeNumber("0033690123456"))
        assertEquals("+33690", CallRuleEngine.normalizePrefix("+33690"))
        val known = CallRuleEngine(
            silenceOutsideDirectory = true,
            directoryReady = true,
            directoryContains = { it == "+590690123456" }
        )
        assertEquals(CallRuleEngine.Action.ALLOW, known.evaluate("+33690123456").action)
    }

    @Test fun historicalMetropolitanFingerprintStillBlocksOverseasNumber() {
        val legacy = "+33690123456"
        val fingerprint = "c".repeat(64)
        val engine = CallRuleEngine(
            setOf(fingerprint),
            fingerprintsForNumber = { setOf(if (it == legacy) fingerprint else "d".repeat(64)) }
        )
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("0690 12 34 56").action)
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("+590690123456").action)
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("0612345678").action)
    }

    @Test fun historicalMetropolitanPrefixStillBlocksOverseasNumber() {
        val engine = CallRuleEngine(blockedPrefixes = setOf("+33690"))
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("0690123456").action)
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("0612345678").action)
    }

    @Test fun saintPierreUsesSixDigitsAfter508() {
        assertEquals("+508410974", CallRuleEngine.normalizeNumber("0508410974"))
        assertEquals("+508410974", CallRuleEngine.normalizeNumber("+508 41 09 74"))
        assertEquals("+508410974", CallRuleEngine.normalizeNumber("00508410974"))
        assertEquals("+508410974", CallRuleEngine.normalizeNumber("+33 508 41 09 74"))
        assertEquals("+508", CallRuleEngine.normalizePrefix("0508"))
        assertEquals("+33556123456", CallRuleEngine.normalizeNumber("0556123456"))
        assertEquals("+508708401234", CallRuleEngine.normalizeNumber("+508708401234"))
    }

    @Test fun saintPierreContactMatchesNationalAndE164() {
        val known = CallRuleEngine(
            silenceOutsideDirectory = true,
            directoryReady = true,
            directoryContains = { it == "+508410974" }
        )
        assertEquals(CallRuleEngine.Action.ALLOW, known.evaluate("05 08 41 09 74").action)
        assertEquals(CallRuleEngine.Action.ALLOW, known.evaluate("+508410974").action)
        assertEquals(CallRuleEngine.Action.SILENCE, known.evaluate("0556123456").action)
    }

    @Test fun historicalSaintPierreFingerprintAndPrefixStillBlock() {
        val legacy = "+33508410974"
        val fingerprint = "e".repeat(64)
        val exact = CallRuleEngine(
            setOf(fingerprint),
            fingerprintsForNumber = { setOf(if (it == legacy) fingerprint else "f".repeat(64)) }
        )
        assertEquals(CallRuleEngine.Action.BLOCK, exact.evaluate("0508410974").action)
        assertEquals(CallRuleEngine.Action.BLOCK, exact.evaluate("+508410974").action)
        assertTrue(CallRuleEngine.blockMatchInputs("0508410974").contains(legacy))
        val prefix = CallRuleEngine(blockedPrefixes = setOf("+33508"))
        assertEquals(CallRuleEngine.Action.BLOCK, prefix.evaluate("0508410974").action)
        assertEquals(CallRuleEngine.Action.ALLOW, prefix.evaluate("0556123456").action)
    }

    @Test fun verifiedArcepTerritoryRootsKeepTheirCountryCode() {
        assertEquals("+590591123456", CallRuleEngine.normalizeNumber("0591123456"))
        assertEquals("+596597123456", CallRuleEngine.normalizeNumber("0597123456"))
        assertEquals("+262263123456", CallRuleEngine.normalizeNumber("0263123456"))
        assertEquals("+262269123456", CallRuleEngine.normalizeNumber("0269123456"))
        assertEquals("+262639123456", CallRuleEngine.normalizeNumber("0639123456"))
        assertEquals("+262268701234", CallRuleEngine.normalizeNumber("0268701234"))
        assertEquals("+262268751234", CallRuleEngine.normalizeNumber("0268751234"))
        assertEquals("+262268812345", CallRuleEngine.normalizeNumber("0268812345"))
        assertEquals("+262268912345", CallRuleEngine.normalizeNumber("0268912345"))
        assertEquals("+590976012345", CallRuleEngine.normalizeNumber("0976012345"))
        assertEquals("+594976412345", CallRuleEngine.normalizeNumber("0976412345"))
        assertEquals("+596976612345", CallRuleEngine.normalizeNumber("0976612345"))
        assertEquals("+262976212345", CallRuleEngine.normalizeNumber("0976212345"))
        assertEquals("+262976912345", CallRuleEngine.normalizeNumber("0976912345"))
        assertEquals("+262990012345", CallRuleEngine.normalizeNumber("0990012345"))
        assertEquals("+590591123456", CallRuleEngine.normalizeNumber("+33591123456"))
        assertEquals("+33264123456", CallRuleEngine.normalizeNumber("0264123456"))
        assertEquals("+33977012345", CallRuleEngine.normalizeNumber("0977012345"))
        assertEquals("+33268123456", CallRuleEngine.normalizeNumber("0268123456"))
    }

    @Test fun historicalMayotteFingerprintStillMatchesFoldedIdentity() {
        val legacy = "+33269123456"
        val fingerprint = "1".repeat(64)
        val engine = CallRuleEngine(
            setOf(fingerprint),
            fingerprintsForNumber = { setOf(if (it == legacy) fingerprint else "2".repeat(64)) }
        )
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("0269123456").action)
        val removed = CallRuleEngine.removalFingerprints("0269123456") { candidate ->
            setOf(if (candidate == legacy) fingerprint else "3".repeat(64))
        }
        assertTrue(removed.contains(fingerprint))
        val other = CallRuleEngine.removalFingerprints("0612345678") { candidate ->
            setOf(if (candidate == legacy) fingerprint else "3".repeat(64))
        }
        assertTrue(!other.contains(fingerprint))
    }
}
