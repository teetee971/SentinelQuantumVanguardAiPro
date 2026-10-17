package com.sentinel.quantum.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceAggregatorTest {
    private val identity = SentinelIdentityKey.phoneFingerprint("fp-test", 1)

    private fun reputationEvent(
        eventId: String,
        observedAtMs: Long,
        expiresAtMs: Long?,
        score: Int? = 80,
        trust: EvidenceProvenance.TrustClass = EvidenceProvenance.TrustClass.SIGNED_REMOTE_ASSERTION,
        signed: Boolean = trust == EvidenceProvenance.TrustClass.SIGNED_REMOTE_ASSERTION,
        communitySignals: Int = 0
    ) = IntelligenceEvent(
        eventId = eventId,
        identityKey = identity,
        evidence = IntelligenceEvidence.Reputation(
            riskScore = score,
            communitySignals = communitySignals,
            categoryCodes = listOf("fraud"),
            sourceLabel = "test",
            signedPackageVerified = signed
        ),
        observedAtMs = observedAtMs,
        expiresAtMs = expiresAtMs,
        source = EvidenceSource.REPUTATION_REMOTE,
        provenance = EvidenceProvenance(
            producer = EvidenceProvenance.Producer.REPUTATION_CORE,
            producerVersion = 1,
            trust = trust
        ),
        privacyClass = if (expiresAtMs == null) PrivacyClass.DURABLE_LOCAL_IDENTITY else PrivacyClass.BOUNDED_METADATA
    )

    private fun contactEvent(eventId: String, observedAtMs: Long) = IntelligenceEvent(
        eventId = eventId,
        identityKey = identity,
        evidence = IntelligenceEvidence.ContactIdentity(
            contactState = IntelligenceEvidence.ContactState.KNOWN,
            displayLabel = "Alice",
            organisationLabel = null,
            contactAlias = "contact-1"
        ),
        observedAtMs = observedAtMs,
        expiresAtMs = null,
        source = EvidenceSource.ANDROID_CONTACT_PROVIDER,
        provenance = EvidenceProvenance(
            producer = EvidenceProvenance.Producer.CONTACT_PROVIDER,
            producerVersion = 1,
            trust = EvidenceProvenance.TrustClass.LOCAL_PLATFORM_ASSERTION
        ),
        privacyClass = PrivacyClass.DURABLE_LOCAL_IDENTITY
    )

    @Test
    fun same_evidence_and_clock_yield_equal_snapshot() {
        val aggregator = IntelligenceAggregator()
        val events = listOf(reputationEvent("r1", 1_000L, 2_000L))

        assertEquals(
            aggregator.aggregate(identity, events, nowMs = 1_500L),
            aggregator.aggregate(identity, events, nowMs = 1_500L)
        )
    }

    @Test
    fun duplicate_event_id_is_idempotent_and_revision_counts_accepted_set_only() {
        val aggregator = IntelligenceAggregator()
        val event = reputationEvent("same", 1_000L, 2_000L)

        val one = aggregator.aggregate(identity, listOf(event), nowMs = 1_500L)
        val duplicated = aggregator.aggregate(identity, listOf(event, event), nowMs = 1_500L)

        assertEquals(one, duplicated)
        assertEquals(1L, duplicated.revision)
    }

    @Test
    fun expired_reputation_is_stale_and_contributes_no_numeric_risk() {
        val snapshot = IntelligenceAggregator().aggregate(
            identity,
            listOf(reputationEvent("expired", 1_000L, 1_100L, score = 91)),
            nowMs = 2_000L
        )

        assertTrue(snapshot.activeEvidence.isEmpty())
        assertEquals(listOf("expired"), snapshot.staleEvidence.map { it.eventId })
        assertNull(snapshot.riskAssessment.numericScore)
        assertEquals(Freshness.STALE, snapshot.riskAssessment.freshness)
    }

    @Test
    fun evidence_without_ttl_has_unknown_freshness_not_fresh() {
        val snapshot = IntelligenceAggregator().aggregate(
            identity,
            listOf(contactEvent("contact", 1_000L)),
            nowMs = 1_500L
        )

        assertEquals(Freshness.UNKNOWN, snapshot.riskAssessment.freshness)
        assertFalse(snapshot.riskAssessment.freshness == Freshness.FRESH)
    }

    @Test
    fun future_timestamp_outside_allowed_skew_is_unknown_and_not_numeric() {
        val snapshot = IntelligenceAggregator(maxFutureSkewMs = 100L).aggregate(
            identity,
            listOf(reputationEvent("future", 2_000L, 3_000L, score = 88)),
            nowMs = 1_000L
        )

        assertTrue(snapshot.activeEvidence.isEmpty())
        assertEquals(Freshness.UNKNOWN, snapshot.riskAssessment.freshness)
        assertNull(snapshot.riskAssessment.numericScore)
        assertTrue(snapshot.reasons.any { it.eventId == "future" && it.state == EvidenceState.INVALID_TIME })
    }

    @Test
    fun community_only_reputation_cannot_grant_blocking_authority() {
        val snapshot = IntelligenceAggregator().aggregate(
            identity,
            listOf(
                reputationEvent(
                    "community",
                    1_000L,
                    2_000L,
                    score = 99,
                    trust = EvidenceProvenance.TrustClass.COMMUNITY_AGGREGATE,
                    signed = false,
                    communitySignals = 100
                )
            ),
            nowMs = 1_500L
        )

        assertFalse(snapshot.riskAssessment.enforcementAuthorized)
    }

    @Test
    fun conflicting_contact_and_signed_reputation_are_both_preserved_as_reasons() {
        val snapshot = IntelligenceAggregator().aggregate(
            identity,
            listOf(contactEvent("contact", 900L), reputationEvent("risk", 1_000L, 2_000L, score = 90)),
            nowMs = 1_500L
        )

        assertTrue(snapshot.reasons.any { it.eventId == "contact" })
        assertTrue(snapshot.reasons.any { it.eventId == "risk" })
        assertEquals(90, snapshot.riskAssessment.numericScore)
    }

    @Test
    fun input_order_does_not_change_snapshot() {
        val aggregator = IntelligenceAggregator()
        val contact = contactEvent("contact", 900L)
        val reputation = reputationEvent("risk", 1_000L, 2_000L, score = 75)

        assertEquals(
            aggregator.aggregate(identity, listOf(contact, reputation), nowMs = 1_500L),
            aggregator.aggregate(identity, listOf(reputation, contact), nowMs = 1_500L)
        )
    }

    @Test
    fun revision_changes_only_when_accepted_evidence_set_changes() {
        val aggregator = IntelligenceAggregator()
        val first = reputationEvent("r1", 1_000L, 3_000L, score = 70)
        val second = reputationEvent("r2", 1_100L, 3_000L, score = 80)

        val one = aggregator.aggregate(identity, listOf(first), nowMs = 1_500L)
        val same = aggregator.aggregate(identity, listOf(first, first), nowMs = 1_500L)
        val changed = aggregator.aggregate(identity, listOf(first, second), nowMs = 1_500L)

        assertEquals(one.revision, same.revision)
        assertTrue(changed.revision > same.revision)
    }
}
