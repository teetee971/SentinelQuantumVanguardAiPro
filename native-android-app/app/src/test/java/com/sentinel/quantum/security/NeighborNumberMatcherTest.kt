package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeighborNumberMatcherTest {
    private val directory = setOf("+33612345678")

    @Test fun oneSubstitutedDigitIsNear() {
        assertTrue(NeighborNumberMatcher.isNear("+33612345679", directory))
    }

    @Test fun exactContactIsNotNear() {
        assertFalse(NeighborNumberMatcher.isNear("+33612345678", directory))
    }

    @Test fun twoDigitsOrADifferentLengthAreNotNear() {
        assertFalse(NeighborNumberMatcher.isNear("+33612345600", directory))
        assertFalse(NeighborNumberMatcher.isNear("+336123456789", directory))
        assertFalse(NeighborNumberMatcher.isNear("+3361234567", directory))
    }

    @Test fun shortAndNonInternationalNumbersAreNotNear() {
        assertFalse(NeighborNumberMatcher.isNear("112", setOf("113")))
        assertFalse(NeighborNumberMatcher.isNear("0612345679", directory))
    }

    @Test fun anEmptyDirectoryDoesNotLabelAnyone() {
        assertFalse(NeighborNumberMatcher.isNear("+33612345679", emptySet()))
    }
}

class NeighborSilencePolicyTest {
    private val directory = setOf("+33612345678")
    private val near: (String) -> Boolean = { NeighborNumberMatcher.isNear(it, directory) }

    @Test fun neighborSilenceStaysOffUnlessChosen() {
        val engine = CallRuleEngine(
            directoryReady = true,
            directoryContains = directory::contains,
            nearDirectory = near
        )
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("+33612345679").action)
    }

    @Test fun chosenNeighborSilenceDoesNotRejectAndLetsTheExactContactRing() {
        val engine = CallRuleEngine(
            silenceNearDirectory = true,
            directoryReady = true,
            directoryContains = directory::contains,
            nearDirectory = near
        )
        val nearDecision = engine.evaluate("+33612345679")
        assertEquals(CallRuleEngine.Action.SILENCE, nearDecision.action)
        assertEquals("NEAR_DIRECTORY_CONTACT", nearDecision.reason)
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("+33612345678").action)
    }

    @Test fun neighborSilenceFailsOpenUntilTheDirectoryIsReady() {
        val engine = CallRuleEngine(
            silenceNearDirectory = true,
            directoryReady = false,
            nearDirectory = near
        )
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("+33612345679").action)
    }

    @Test fun anExplicitBlockStillRejectsANearNumber() {
        val engine = CallRuleEngine(
            blockedPrefixes = setOf("+33612"),
            silenceNearDirectory = true,
            directoryReady = true,
            directoryContains = directory::contains,
            nearDirectory = near
        )
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("+33612345679").action)
    }

    @Test fun unknownSilenceReasonStaysUnchangedWhenTheNumberIsNotNear() {
        val engine = CallRuleEngine(
            silenceOutsideDirectory = true,
            silenceNearDirectory = true,
            directoryReady = true,
            directoryContains = directory::contains,
            nearDirectory = near
        )
        assertEquals("NOT_IN_DIRECTORY", engine.evaluate("+33700000000").reason)
    }
}

class PublishedCatalogRefreshPreferenceTest {
    @Test fun absentOrFalseDoesNotConsent() {
        assertFalse(PublishedCatalogRefreshPreference.resolved(null))
        assertFalse(PublishedCatalogRefreshPreference.resolved(false))
    }

    @Test fun onlyStoredTrueConsents() {
        assertTrue(PublishedCatalogRefreshPreference.resolved(true))
    }
}
