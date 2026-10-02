package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcepVerifiedPrefixCatalogTest {
    @Test
    fun allPublishedPrefixesAreCanonicalAndBounded() {
        assertEquals(22, ArcepVerifiedPrefixCatalog.entries.size)
        assertEquals(22, ArcepVerifiedPrefixCatalog.e164Prefixes.size)
        ArcepVerifiedPrefixCatalog.entries.forEach { entry ->
            assertTrue(entry.nationalRoot.startsWith("0"))
            assertEquals(entry.e164Prefix, CallRuleEngine.normalizePrefix(entry.e164Prefix))
        }
    }

    @Test
    fun guadeloupeRangesUseCanonicalE164Prefixes() {
        assertNotNull(ArcepVerifiedPrefixCatalog.findByE164Prefix("+5905987"))
        assertNotNull(ArcepVerifiedPrefixCatalog.findByE164Prefix("+5909475"))
    }

    @Test
    fun arcepPrefixCanBlockOnlyWhenPassedAsExplicitBlockingRule() {
        val disabled = CallRuleEngine()
        assertEquals(
            CallRuleEngine.Action.ALLOW,
            disabled.evaluate("+590 9475 12 34 56").action
        )

        val enabled = CallRuleEngine(
            blockedPrefixes = ArcepVerifiedPrefixCatalog.e164Prefixes
        )
        assertEquals(
            CallRuleEngine.Action.BLOCK,
            enabled.evaluate("+590 9475 12 34 56").action
        )
    }
}
