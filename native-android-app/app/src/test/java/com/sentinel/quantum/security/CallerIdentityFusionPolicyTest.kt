package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerIdentityFusionPolicyTest {
    private val now = 2_000L

    @Test
    fun `community identity can never become verified`() {
        val result = CallerIdentityFusionPolicy.fuse(
            listOf(
                CallerIdentityEvidence(
                    displayName = "Nom communautaire",
                    source = "Communauté Sentinel",
                    kind = CallerIdentityEvidenceKind.COMMUNITY_REPORT,
                    observedAtEpochSeconds = 1_900L,
                    ttlSeconds = 500L
                )
            ),
            now
        )
        assertEquals(CallerIdentityConfidence.INDICATIVE, result.confidence)
    }

    @Test
    fun `heuristic identity can never become verified`() {
        val result = CallerIdentityFusionPolicy.fuse(
            listOf(
                CallerIdentityEvidence(
                    organisation = "Organisation supposée",
                    source = "Analyse Sentinel",
                    kind = CallerIdentityEvidenceKind.HEURISTIC,
                    observedAtEpochSeconds = 1_999L,
                    ttlSeconds = 500L
                )
            ),
            now
        )
        assertEquals(CallerIdentityConfidence.INDICATIVE, result.confidence)
    }

    @Test
    fun `expired authoritative evidence is not trusted`() {
        val result = CallerIdentityFusionPolicy.fuse(
            listOf(
                CallerIdentityEvidence(
                    displayName = "Ancien contact",
                    source = "Annuaire",
                    kind = CallerIdentityEvidenceKind.AUTHORITATIVE_DIRECTORY,
                    observedAtEpochSeconds = 1_000L,
                    ttlSeconds = 100L
                )
            ),
            now
        )
        assertEquals(CallerIdentityConfidence.UNKNOWN, result.confidence)
        assertNull(result.displayName)
    }

    @Test
    fun `verified fresh evidence outranks community evidence`() {
        val result = CallerIdentityFusionPolicy.fuse(
            listOf(
                CallerIdentityEvidence(
                    displayName = "Nom signalé",
                    source = "Communauté Sentinel",
                    kind = CallerIdentityEvidenceKind.COMMUNITY_REPORT,
                    observedAtEpochSeconds = 1_999L,
                    ttlSeconds = 500L
                ),
                CallerIdentityEvidence(
                    displayName = "Contact local",
                    source = "Contacts",
                    kind = CallerIdentityEvidenceKind.LOCAL_CONTACT,
                    observedAtEpochSeconds = 1_900L,
                    ttlSeconds = 500L
                )
            ),
            now
        )
        assertEquals("Contact local", result.displayName)
        assertEquals(CallerIdentityConfidence.VERIFIED, result.confidence)
        assertEquals(2, result.evidenceCount)
    }

    @Test
    fun `signed proof requires proof id before verified status`() {
        val result = CallerIdentityFusionPolicy.fuse(
            listOf(
                CallerIdentityEvidence(
                    displayName = "Identité distante",
                    source = "Preuve signée",
                    kind = CallerIdentityEvidenceKind.SIGNED_IDENTITY_PROOF,
                    observedAtEpochSeconds = 1_999L,
                    ttlSeconds = 60L,
                    proofId = null
                )
            ),
            now
        )
        assertEquals(CallerIdentityConfidence.INDICATIVE, result.confidence)
    }
}
