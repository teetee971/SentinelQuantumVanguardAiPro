package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningLineRegionPolicyTest {
    @Test fun aNationalNumberBlockedOnTheOnlyPlanThatParsesItIsBlocked() {
        InternationalNumberParser.warm()
        val decision = ScreeningLineRegionPolicy.decide(setOf("US", "FR")) { home ->
            CallRuleEngine(blockedPrefixes = setOf("+33612"), homeRegion = home).evaluate("0612345678")
        }
        assertEquals(CallRuleEngine.Action.BLOCK, decision.action)
        assertEquals("+33612345678", decision.normalizedNumber)
    }

    @Test fun twoValidParsesThatDisagreeFailOpen() {
        InternationalNumberParser.warm()
        val decision = ScreeningLineRegionPolicy.decide(setOf("FR", "BE")) { home ->
            CallRuleEngine(blockedPrefixes = setOf("+32470"), homeRegion = home).evaluate("0470123456")
        }
        assertEquals(CallRuleEngine.Action.ALLOW, decision.action)
        assertEquals("AMBIGUOUS_LINE", decision.reason)
    }

    @Test fun aSingleInstalledRegionKeepsTheExistingDecision() {
        val decision = ScreeningLineRegionPolicy.decide(setOf("FR")) { home ->
            CallRuleEngine(blockedPrefixes = setOf("+33612"), homeRegion = home).evaluate("0612345678")
        }
        assertEquals(CallRuleEngine.Action.BLOCK, decision.action)
    }

    @Test fun aContactOnOneParseIsNotBlockedByAnotherParse() {
        InternationalNumberParser.warm()
        val decision = ScreeningLineRegionPolicy.decide(setOf("FR", "BE")) { home ->
            val belgian = CallRuleEngine.normalizeNumber("0470123456", home) == "+32470123456"
            CallRuleEngine(
                blockedPrefixes = setOf("+33470"),
                silenceOutsideDirectory = true,
                directoryReady = true,
                directoryContains = { belgian && it == "+32470123456" },
                homeRegion = home
            ).evaluate("0470123456")
        }
        assertEquals(CallRuleEngine.Action.ALLOW, decision.action)
        assertEquals("IN_DIRECTORY", decision.reason)
    }
}
