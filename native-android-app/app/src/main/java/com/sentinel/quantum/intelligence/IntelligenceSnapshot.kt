package com.sentinel.quantum.intelligence

enum class Freshness {
    FRESH,
    STALE,
    UNKNOWN
}

enum class EvidenceState {
    ACTIVE,
    STALE,
    UNKNOWN_FRESHNESS,
    INVALID_TIME
}

data class EvidenceReason(
    val eventId: String,
    val source: EvidenceSource,
    val state: EvidenceState,
    val provenance: EvidenceProvenance
)

data class RiskAssessment(
    val numericScore: Int?,
    val freshness: Freshness,
    val enforcementAuthorized: Boolean
)

data class IntelligenceSnapshot(
    val identityKey: SentinelIdentityKey,
    val revision: Long,
    val activeEvidence: List<IntelligenceEvent>,
    val staleEvidence: List<IntelligenceEvent>,
    val reasons: List<EvidenceReason>,
    val riskAssessment: RiskAssessment
)
