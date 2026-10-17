package com.sentinel.quantum.security

import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationDiagnosticReason
import com.sentinel.quantum.integration.IntegrationState
import com.sentinel.quantum.integration.IntegrationTruthRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcepVerifiedPrefixCatalogTest {
    @After
    fun clearRuntimeTruth() {
        IntegrationTruthRuntime.clearForTests()
    }

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
    fun validatedRuntimeUsePublishesFreshPartialRegulatoryTruth() {
        val observedAt = 10_000L
        assertTrue(ArcepVerifiedPrefixCatalog.observeValidatedRuntimeUse(observedAt))
        val status = IntegrationTruthRuntime.snapshot(observedAt)
            .status(IntegrationCapabilityId.REGULATORY_INTELLIGENCE)
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, status.state)
        assertEquals(IntegrationDiagnosticReason.PARTIAL_PUBLIC_SOURCES_ONLY, status.diagnosticReason)
    }

    @Test
    fun runtimeValidationRejectsMalformedOrDuplicateCatalogEntries() {
        val malformed = listOf(
            ArcepVerifiedPrefixCatalog.Entry("Test", "05987", "+5905987"),
            ArcepVerifiedPrefixCatalog.Entry("Duplicate", "05987", "+5905987")
        )
        assertFalse(ArcepVerifiedPrefixCatalog.isValidRuntimeCatalog(malformed))
        val status = IntegrationTruthRuntime.snapshot(10_000L)
            .status(IntegrationCapabilityId.REGULATORY_INTELLIGENCE)
        assertEquals(IntegrationState.LOCKED_EXTERNAL_DEPENDENCY, status.state)
        assertEquals(IntegrationDiagnosticReason.RUNTIME_NOT_OBSERVED, status.diagnosticReason)
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

    @Test
    fun snapshotKeepsManualRulesSeparateFromEffectiveArcepRules() {
        val manual = setOf("+33162")
        val disabled = CallBlocklistStore.Snapshot(
            blockedNumberHashes = emptySet(),
            blockedPrefixes = manual,
            signedSilencePrefixes = emptySet(),
            arcepVerifiedBlockingEnabled = false
        )
        assertEquals(manual, disabled.blockedPrefixes)
        assertEquals(manual, disabled.effectiveBlockedPrefixes)

        val enabled = disabled.copy(arcepVerifiedBlockingEnabled = true)
        assertEquals(manual, enabled.blockedPrefixes)
        assertTrue(enabled.effectiveBlockedPrefixes.contains("+5909475"))
        assertTrue(enabled.effectiveBlockedPrefixes.contains("+33948"))
        assertFalse(disabled.effectiveBlockedPrefixes.contains("+5909475"))
    }
}
