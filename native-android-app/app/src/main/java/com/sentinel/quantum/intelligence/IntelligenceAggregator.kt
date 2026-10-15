package com.sentinel.quantum.intelligence

/**
 * Pure deterministic projection of retained intelligence evidence.
 *
 * It does not perform IO, remote lookups, persistence or enforcement. Ordering and duplicate
 * handling are local and deterministic for the same evidence set and supplied clock.
 */
class IntelligenceAggregator(
    private val maxFutureSkewMs: Long = DEFAULT_MAX_FUTURE_SKEW_MS
) {
    init {
        require(maxFutureSkewMs >= 0L) { "maxFutureSkewMs must be non-negative" }
    }

    fun aggregate(
        identityKey: SentinelIdentityKey,
        events: List<IntelligenceEvent>,
        nowMs: Long
    ): IntelligenceSnapshot {
        require(nowMs >= 0L) { "nowMs must be non-negative" }

        val accepted = events
            .asSequence()
            .filter { it.identityKey == identityKey }
            .sortedWith(compareBy<IntelligenceEvent>({ it.observedAtMs }, { it.eventId }))
            .distinctBy { it.eventId }
            .toList()

        val active = mutableListOf<IntelligenceEvent>()
        val stale = mutableListOf<IntelligenceEvent>()
        val reasons = mutableListOf<EvidenceReason>()

        accepted.forEach { event ->
            val state = classify(event, nowMs)
            when (state) {
                EvidenceState.ACTIVE,
                EvidenceState.UNKNOWN_FRESHNESS -> active += event
                EvidenceState.STALE -> stale += event
                EvidenceState.INVALID_TIME -> Unit
            }
            reasons += EvidenceReason(
                eventId = event.eventId,
                source = event.source,
                state = state,
                provenance = event.provenance
            )
        }

        val freshReputation = active
            .asSequence()
            .filter { event -> classify(event, nowMs) == EvidenceState.ACTIVE }
            .filter { it.evidence is IntelligenceEvidence.Reputation }
            .sortedWith(compareBy<IntelligenceEvent>({ it.observedAtMs }, { it.eventId }))
            .lastOrNull()

        val score = (freshReputation?.evidence as? IntelligenceEvidence.Reputation)?.riskScore
        val hasStaleReputation = stale.any { it.evidence is IntelligenceEvidence.Reputation }
        val hasInvalidReputation = reasons.any { reason ->
            reason.state == EvidenceState.INVALID_TIME &&
                accepted.any { it.eventId == reason.eventId && it.evidence is IntelligenceEvidence.Reputation }
        }

        val freshness = when {
            freshReputation != null -> Freshness.FRESH
            hasStaleReputation -> Freshness.STALE
            hasInvalidReputation -> Freshness.UNKNOWN
            else -> Freshness.UNKNOWN
        }

        val enforcementAuthorized = freshReputation?.let { event ->
            val reputation = event.evidence as IntelligenceEvidence.Reputation
            event.provenance.trust == EvidenceProvenance.TrustClass.SIGNED_REMOTE_ASSERTION &&
                reputation.signedPackageVerified
        } ?: false

        return IntelligenceSnapshot(
            identityKey = identityKey,
            revision = accepted.size.toLong(),
            activeEvidence = active.toList(),
            staleEvidence = stale.toList(),
            reasons = reasons.toList(),
            riskAssessment = RiskAssessment(
                numericScore = score,
                freshness = freshness,
                enforcementAuthorized = enforcementAuthorized
            )
        )
    }

    private fun classify(event: IntelligenceEvent, nowMs: Long): EvidenceState {
        if (event.observedAtMs > nowMs && event.observedAtMs - nowMs > maxFutureSkewMs) {
            return EvidenceState.INVALID_TIME
        }
        val expiresAtMs = event.expiresAtMs ?: return EvidenceState.UNKNOWN_FRESHNESS
        return if (expiresAtMs < nowMs) EvidenceState.STALE else EvidenceState.ACTIVE
    }

    companion object {
        const val DEFAULT_MAX_FUTURE_SKEW_MS: Long = 5 * 60 * 1000L
    }
}
