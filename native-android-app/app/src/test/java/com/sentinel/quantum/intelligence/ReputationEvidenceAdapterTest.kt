package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.CallerReputationClient
import com.sentinel.quantum.security.SignedCallRulePackageVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReputationEvidenceAdapterTest {
    private val identity = SentinelIdentityKey.phoneFingerprint("adapter-test", 1)

    @Test
    fun remote_api_result_preserves_bounded_reputation_but_remains_unverified() {
        val result = CallerReputationClient.Result(
            riskScore = 87,
            action = "FLAG",
            flags = listOf("community"),
            categories = listOf("fraud", "wangiri"),
            signals = 9,
            callerCountry = "FR",
            isInternational = false,
            communityIntelligence = "elevated",
            warning = "bounded warning",
            reputationObservedAtMs = 10_000L,
            reputationTtlMs = 5_000L
        )

        val event = ReputationEvidenceAdapter.fromRemoteResult(
            eventId = "remote-1",
            identityKey = identity,
            result = result,
            sourceLabel = "caller-reputation-api"
        )

        val evidence = event.evidence as IntelligenceEvidence.Reputation
        assertEquals(87, evidence.riskScore)
        assertEquals(listOf("fraud", "wangiri"), evidence.categoryCodes)
        assertEquals(9, evidence.communitySignals)
        assertFalse(evidence.signedPackageVerified)
        assertEquals(10_000L, event.observedAtMs)
        assertEquals(15_000L, event.expiresAtMs)
        assertEquals(EvidenceProvenance.TrustClass.UNVERIFIED_REMOTE, event.provenance.trust)
    }

    @Test
    fun accepted_signed_rule_package_becomes_signed_evidence_without_invented_score() {
        val verified = SignedCallRulePackageVerifier.Result(
            accepted = true,
            reason = "SIGNED_RULE_ACCEPTED",
            rulePackage = SignedCallRulePackageVerifier.RulePackage(
                packageId = "fr-vigilance",
                sequence = 12L,
                issuedAtMs = 20_000L,
                expiresAtMs = 30_000L,
                issuerId = "sentinel-rules",
                keyId = "prod-2026",
                silencePrefixes = setOf("+33123")
            )
        )

        val event = ReputationEvidenceAdapter.fromVerifiedSignedPackage(
            eventId = "signed-1",
            identityKey = identity,
            verification = verified
        )

        requireNotNull(event)
        val evidence = event.evidence as IntelligenceEvidence.Reputation
        assertNull(evidence.riskScore)
        assertTrue(evidence.categoryCodes.isEmpty())
        assertEquals(0, evidence.communitySignals)
        assertTrue(evidence.signedPackageVerified)
        assertEquals("fr-vigilance", evidence.sourceLabel)
        assertEquals(20_000L, event.observedAtMs)
        assertEquals(30_000L, event.expiresAtMs)
        assertEquals(EvidenceProvenance.TrustClass.SIGNED_REMOTE_ASSERTION, event.provenance.trust)
        assertEquals("sentinel-rules", event.provenance.issuer)
    }

    @Test
    fun rejected_or_incomplete_signed_package_cannot_become_trusted_evidence() {
        assertNull(
            ReputationEvidenceAdapter.fromVerifiedSignedPackage(
                eventId = "signed-rejected",
                identityKey = identity,
                verification = SignedCallRulePackageVerifier.Result(false, "SIGNED_RULE_SIGNATURE_INVALID")
            )
        )
        assertNull(
            ReputationEvidenceAdapter.fromVerifiedSignedPackage(
                eventId = "signed-missing",
                identityKey = identity,
                verification = SignedCallRulePackageVerifier.Result(true, "SIGNED_RULE_ACCEPTED", null)
            )
        )
    }
}
