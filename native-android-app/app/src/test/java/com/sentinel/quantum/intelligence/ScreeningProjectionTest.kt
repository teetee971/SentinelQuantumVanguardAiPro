package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.CallBlocklistStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScreeningProjectionTest {
    @Test
    fun `projection exposes only pre-authorized call rules`() {
        val callRules = CallBlocklistStore.Snapshot(
            blockedNumberHashes = setOf("v1:" + "a".repeat(64)),
            blockedPrefixes = setOf("+33948"),
            signedSilencePrefixes = setOf("+33162"),
            signedExpiresAtMs = 2_000L
        )
        val intelligence = highRiskUnverifiedSnapshot(revision = 7L)

        val projection = ScreeningProjection.from(callRules, intelligence, nowMs = 1_000L)

        assertEquals(callRules.blockedNumberHashes, projection.blockedNumberHashes)
        assertEquals(callRules.blockedPrefixes, projection.blockedPrefixes)
        assertEquals(callRules.signedSilencePrefixes, projection.signedSilencePrefixes)
        assertEquals(7L, projection.intelligenceRevision)
        assertFalse(projection.toString().contains("Alice Example"))
        assertFalse(projection.toString().contains("REMOTE_HIGH_RISK"))
        assertFalse(projection.toString().contains("99"))
    }

    @Test
    fun `expired signed rules are excluded while user rules remain`() {
        val callRules = CallBlocklistStore.Snapshot(
            blockedNumberHashes = setOf("v1:" + "b".repeat(64)),
            blockedPrefixes = setOf("+33949"),
            signedSilencePrefixes = setOf("+33163"),
            signedExpiresAtMs = 999L
        )

        val projection = ScreeningProjection.from(callRules, null, nowMs = 1_000L)

        assertEquals(callRules.blockedNumberHashes, projection.blockedNumberHashes)
        assertEquals(callRules.blockedPrefixes, projection.blockedPrefixes)
        assertEquals(emptySet<String>(), projection.signedSilencePrefixes)
        assertEquals(null, projection.intelligenceRevision)
    }

    @Test
    fun `projection defensively copies mutable rule inputs`() {
        val hashes = mutableSetOf("v1:" + "c".repeat(64))
        val prefixes = mutableSetOf("+33950")
        val signed = mutableSetOf("+33164")
        val projection = ScreeningProjection.from(
            CallBlocklistStore.Snapshot(hashes, prefixes, signed, signedExpiresAtMs = 2_000L),
            null,
            nowMs = 1_000L
        )

        hashes.clear()
        prefixes.clear()
        signed.clear()

        assertEquals(1, projection.blockedNumberHashes.size)
        assertEquals(setOf("+33950"), projection.blockedPrefixes)
        assertEquals(setOf("+33164"), projection.signedSilencePrefixes)
    }

    @Test
    fun `cache replacement is atomic and never mutates the previous projection`() {
        val first = ScreeningProjection.from(
            CallBlocklistStore.Snapshot(setOf("v1:" + "d".repeat(64)), setOf("+33951"), emptySet()),
            null,
            nowMs = 1_000L
        )
        val second = ScreeningProjection.from(
            CallBlocklistStore.Snapshot(setOf("v1:" + "e".repeat(64)), setOf("+33952"), setOf("+33165"), signedExpiresAtMs = 3_000L),
            highRiskUnverifiedSnapshot(revision = 9L),
            nowMs = 1_000L
        )
        val cache = ScreeningProjectionCache(first)
        val observed = Collections.synchronizedList(mutableListOf<ScreeningProjection>())
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        repeat(4) {
            pool.submit {
                start.await()
                repeat(2_000) { observed += cache.current() }
            }
        }

        start.countDown()
        cache.replace(second)
        pool.shutdown()
        assertEquals(true, pool.awaitTermination(5, TimeUnit.SECONDS))

        assertSame(second, cache.current())
        assertNotSame(first, second)
        assertEquals(setOf("+33951"), first.blockedPrefixes)
        assertEquals(setOf("+33952"), second.blockedPrefixes)
        assertEquals(true, observed.all { it === first || it === second })
    }

    private fun highRiskUnverifiedSnapshot(revision: Long): IntelligenceSnapshot {
        val identityKey = SentinelIdentityKey.phoneFingerprint("local-fingerprint", 1)
        val provenance = EvidenceProvenance(
            producer = EvidenceProvenance.Producer.REPUTATION_CORE,
            producerVersion = 1,
            trust = EvidenceProvenance.TrustClass.UNVERIFIED_REMOTE
        )
        val reputation = IntelligenceEvent(
            eventId = "remote-risk",
            identityKey = identityKey,
            evidence = IntelligenceEvidence.Reputation(
                riskScore = 99,
                communitySignals = 20_000,
                categoryCodes = listOf("REMOTE_HIGH_RISK"),
                sourceLabel = "Alice Example",
                signedPackageVerified = false
            ),
            observedAtMs = 1_000L,
            expiresAtMs = 2_000L,
            source = EvidenceSource.REPUTATION_REMOTE,
            provenance = provenance,
            privacyClass = PrivacyClass.BOUNDED_METADATA
        )
        return IntelligenceSnapshot(
            identityKey = identityKey,
            revision = revision,
            activeEvidence = listOf(reputation),
            staleEvidence = emptyList(),
            reasons = listOf(EvidenceReason("remote-risk", EvidenceSource.REPUTATION_REMOTE, EvidenceState.ACTIVE, provenance)),
            riskAssessment = RiskAssessment(99, Freshness.FRESH, enforcementAuthorized = false)
        )
    }
}
