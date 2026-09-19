package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CallerIdProvenanceTest {
    @Test fun localContactRemainsLocalContactEvidence() {
        val evidence = CallerIdProvenance.localIdentity("Alice", "Example")
        assertNotNull(evidence)
        assertEquals(ProtectionProvenance.Source.LOCAL_CONTACT, evidence!!.source)
        assertEquals(ProtectionProvenance.Confidence.VERIFIED, evidence.confidence)
        assertFalse(ProtectionProvenance.canAssertVerifiedFact(evidence))
    }

    @Test fun emptyLocalIdentityProducesNoEvidence() {
        assertNull(CallerIdProvenance.localIdentity(" ", null))
    }

    @Test fun sentinelDecisionIsOnlyIndicativeAnalysis() {
        val evidence = CallerIdProvenance.sentinelDecision("SIGNED_REPUTATION_PREFIX")
        assertEquals(ProtectionProvenance.Source.SENTINEL_ANALYSIS, evidence.source)
        assertEquals(ProtectionProvenance.Confidence.INDICATIVE, evidence.confidence)
        assertFalse(ProtectionProvenance.canAssertVerifiedFact(evidence))
    }

    @Test fun communitySignalCannotBecomeVerifiedFact() {
        val evidence = CallerIdProvenance.communitySignal("Wangiri reports")
        assertEquals(ProtectionProvenance.Source.COMMUNITY_REPORT, evidence.source)
        assertFalse(ProtectionProvenance.canAssertVerifiedFact(evidence))
    }
}
