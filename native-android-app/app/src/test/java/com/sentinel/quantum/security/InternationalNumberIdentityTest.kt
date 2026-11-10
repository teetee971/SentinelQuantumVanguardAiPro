package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InternationalNumberIdentityTest {
    @Test fun frenchHomeStillFoldsNationalAndOverseasNumbers() {
        assertEquals("+33612345678", CallRuleEngine.normalizeNumber("0612345678"))
        assertEquals("+590690123456", CallRuleEngine.normalizeNumber("0690123456"))
        assertEquals("+508123456", CallRuleEngine.normalizeNumber("0508123456"))
        assertEquals("+32470123456", CallRuleEngine.normalizeNumber("+32 470 12 34 56"))
        assertEquals("+447911123456", CallRuleEngine.normalizeNumber("00447911123456"))
    }

    @Test fun nonFrenchHomeDoesNotRewriteANationalNumberAsFrance() {
        assertNull(CallRuleEngine.normalizeNumber("0470123456", homeRegion = "BE"))
        val frenchPrefix = CallRuleEngine(blockedPrefixes = setOf("+33"), homeRegion = "BE")
        assertEquals(CallRuleEngine.Action.ALLOW, frenchPrefix.evaluate("0470123456").action)
        assertEquals(CallRuleEngine.Action.ALLOW, frenchPrefix.evaluate("+32470123456").action)
        val belgianPrefix = CallRuleEngine(blockedPrefixes = setOf("+32"), homeRegion = "BE")
        assertEquals(CallRuleEngine.Action.BLOCK, belgianPrefix.evaluate("+32470123456").action)
    }

    @Test fun foreignAndPremiumLabelsDoNotBlock() {
        assertEquals("International", CallRuleEngine.displayKind("+447911123456"))
        assertEquals("Indicatif partagé, pays non déterminé", CallRuleEngine.displayKind("+12025550123"))
        assertEquals("Service / tarification à vérifier", CallRuleEngine.displayKind("0899123456"))
        assertEquals("Saint-Pierre-et-Miquelon", CallRuleEngine.displayKind("0508123456"))
        assertEquals("Mobile", CallRuleEngine.displayKind("0690123456"))
        val engine = CallRuleEngine()
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("0899123456").action)
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("+447911123456").action)
    }

    @Test fun countryPrefixStillMatchesTheCanonicalInternationalForm() {
        val belgium = PhoneCountryPrefixCatalog.resolveNumber("+32 470 12 34 56")
        assertEquals("+32", belgium?.prefix)
        val engine = CallRuleEngine(blockedPrefixes = setOf("+44"))
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("00447911123456").action)
        assertEquals(CallRuleEngine.Action.ALLOW, engine.evaluate("+33612345678").action)
    }
}
