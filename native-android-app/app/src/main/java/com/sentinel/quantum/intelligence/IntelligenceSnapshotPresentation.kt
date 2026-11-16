package com.sentinel.quantum.intelligence

/**
 * Privacy-bounded, presentation-only view of an [IntelligenceSnapshot].
 *
 * Deliberately excludes the pseudonymous identity key and enforcement authorization so UI
 * consumers cannot accidentally turn general intelligence evidence into a blocking decision.
 */
class IntelligenceSnapshotPresentation private constructor(
    val revision: Long,
    val riskScore: Int?,
    val freshness: Freshness,
    val activeEvidenceCount: Int,
    val staleEvidenceCount: Int,
    val sources: List<EvidenceSource>,
    val evidenceStates: Set<EvidenceState>,
    val communityAggregatePresent: Boolean
) {
    override fun toString(): String =
        "IntelligenceSnapshotPresentation(" +
            "revision=$revision, " +
            "riskScore=$riskScore, " +
            "freshness=$freshness, " +
            "activeEvidenceCount=$activeEvidenceCount, " +
            "staleEvidenceCount=$staleEvidenceCount, " +
            "sources=$sources, " +
            "evidenceStates=$evidenceStates, " +
            "communityAggregatePresent=$communityAggregatePresent" +
            ")"

    companion object {
        fun from(snapshot: IntelligenceSnapshot): IntelligenceSnapshotPresentation {
            val sources = snapshot.reasons
                .asSequence()
                .map { it.source }
                .distinct()
                .sortedBy { it.ordinal }
                .toList()
            val evidenceStates = snapshot.reasons
                .asSequence()
                .map { it.state }
                .toSet()
            val communityAggregatePresent = snapshot.reasons.any {
                it.provenance.trust == EvidenceProvenance.TrustClass.COMMUNITY_AGGREGATE
            }

            return IntelligenceSnapshotPresentation(
                revision = snapshot.revision,
                riskScore = snapshot.riskAssessment.numericScore,
                freshness = snapshot.riskAssessment.freshness,
                activeEvidenceCount = snapshot.activeEvidence.size,
                staleEvidenceCount = snapshot.staleEvidence.size,
                sources = sources,
                evidenceStates = evidenceStates,
                communityAggregatePresent = communityAggregatePresent
            )
        }
    }
}
