package com.sentinel.quantum.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceSnapshotPresentationTest {
    @Test
    fun `fresh snapshot maps bounded diagnostics without identity material`() {
        val snapshot = snapshot(
            revision = 7L,
            riskScore = 61,
            freshness = Freshness.FRESH,
            activeSources = listOf(EvidenceSource.CALL_SCREENING, EvidenceSource.REPUTATION_REMOTE),
            staleSources = emptyList()
        )

        val presentation = IntelligenceSnapshotPresentation.from(snapshot)

        assertEquals(7L, presentation.revision)
        assertEquals(61, presentation.riskScore)
        assertEquals(Freshness.FRESH, presentation.freshness)
        assertEquals(2, presentation.activeEvidenceCount)
        assertEquals(0, presentation.staleEvidenceCount)
        assertEquals(
            listOf(EvidenceSource.CALL_SCREENING, EvidenceSource.REPUTATION_REMOTE),
            presentation.sources
        )
        assertEquals(setOf(EvidenceState.ACTIVE), presentation.evidenceStates)
        assertFalse(presentation.toString().contains("v1:private-device-fingerprint"))
    }

    @Test
    fun `stale and unknown freshness remain explicit`() {
        val stale = snapshot(
            revision = 8L,
            riskScore = null,
            freshness = Freshness.STALE,
            activeSources = emptyList(),
            staleSources = listOf(EvidenceSource.REPUTATION_REMOTE)
        )
        val unknown = snapshot(
            revision = 9L,
            riskScore = null,
            freshness = Freshness.UNKNOWN,
            activeSources = listOf(EvidenceSource.SMS),
            staleSources = emptyList(),
            activeState = EvidenceState.UNKNOWN_FRESHNESS
        )

        val stalePresentation = IntelligenceSnapshotPresentation.from(stale)
        val unknownPresentation = IntelligenceSnapshotPresentation.from(unknown)

        assertEquals(Freshness.STALE, stalePresentation.freshness)
        assertEquals(1, stalePresentation.staleEvidenceCount)
        assertEquals(setOf(EvidenceState.STALE), stalePresentation.evidenceStates)
        assertEquals(Freshness.UNKNOWN, unknownPresentation.freshness)
        assertEquals(setOf(EvidenceState.UNKNOWN_FRESHNESS), unknownPresentation.evidenceStates)
    }

    @Test
    fun `community evidence presentation never emits enforcement decision`() {
        val snapshot = snapshot(
            revision = 10L,
            riskScore = 99,
            freshness = Freshness.FRESH,
            activeSources = listOf(EvidenceSource.REPUTATION_REMOTE),
            staleSources = emptyList(),
            trust = EvidenceProvenance.TrustClass.COMMUNITY_AGGREGATE,
            enforcementAuthorized = true
        )

        val presentation = IntelligenceSnapshotPresentation.from(snapshot)

        assertTrue(presentation.communityAggregatePresent)
        assertEquals(99, presentation.riskScore)
        assertFalse(presentation.toString().contains("enforcementAuthorized"))
        assertFalse(presentation.toString().contains("BLOCK"))
    }

    private fun snapshot(
        revision: Long,
        riskScore: Int?,
        freshness: Freshness,
        activeSources: List<EvidenceSource>,
        staleSources: List<EvidenceSource>,
        activeState: EvidenceState = EvidenceState.ACTIVE,
        trust: EvidenceProvenance.TrustClass = EvidenceProvenance.TrustClass.LOCAL_DERIVED,
        enforcementAuthorized: Boolean = false
    ): IntelligenceSnapshot {
        val identity = SentinelIdentityKey.phoneFingerprint("v1:private-device-fingerprint", 1)
        val provenance = EvidenceProvenance(
            producer = EvidenceProvenance.Producer.PHONE_CORE,
            producerVersion = 1,
            trust = trust
        )
        val activeEvents = activeSources.mapIndexed { index, source ->
            event("active-$index", identity, source, provenance)
        }
        val staleEvents = staleSources.mapIndexed { index, source ->
            event("stale-$index", identity, source, provenance)
        }
        val reasons = buildList {
            activeEvents.forEach { event ->
                add(EvidenceReason(event.eventId, event.source, activeState, provenance))
            }
            staleEvents.forEach { event ->
                add(EvidenceReason(event.eventId, event.source, EvidenceState.STALE, provenance))
            }
        }
        return IntelligenceSnapshot(
            identityKey = identity,
            revision = revision,
            activeEvidence = activeEvents,
            staleEvidence = staleEvents,
            reasons = reasons,
            riskAssessment = RiskAssessment(riskScore, freshness, enforcementAuthorized)
        )
    }

    private fun event(
        id: String,
        identity: SentinelIdentityKey,
        source: EvidenceSource,
        provenance: EvidenceProvenance
    ): IntelligenceEvent = IntelligenceEvent(
        eventId = id,
        identityKey = identity,
        evidence = IntelligenceEvidence.CallDecision(
            action = IntelligenceEvidence.CallAction.ALLOW,
            ruleSource = "test",
            reasonCode = "TEST",
            networkVerification = IntelligenceEvidence.NetworkVerification.UNKNOWN
        ),
        observedAtMs = 1_000L,
        expiresAtMs = 2_000L,
        source = source,
        provenance = provenance,
        privacyClass = PrivacyClass.BOUNDED_METADATA
    )
}
