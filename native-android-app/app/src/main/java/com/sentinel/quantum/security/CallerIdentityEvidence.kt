package com.sentinel.quantum.security

/**
 * Provenance-aware identity evidence used by Caller ID.
 *
 * Confidence is intentionally separate from reputation: reports and heuristics must never
 * promote a person's or organisation's identity to VERIFIED.
 */
enum class CallerIdentityConfidence {
    UNKNOWN,
    INDICATIVE,
    CORROBORATED,
    VERIFIED
}

enum class CallerIdentityEvidenceKind {
    LOCAL_CONTACT,
    AUTHORITATIVE_DIRECTORY,
    SIGNED_IDENTITY_PROOF,
    LICENSED_DIRECTORY,
    COMMUNITY_REPORT,
    HEURISTIC
}

data class CallerIdentityEvidence(
    val displayName: String? = null,
    val organisation: String? = null,
    val source: String,
    val kind: CallerIdentityEvidenceKind,
    val observedAtEpochSeconds: Long,
    val ttlSeconds: Long,
    val proofId: String? = null
) {
    fun isFresh(nowEpochSeconds: Long): Boolean =
        ttlSeconds > 0 &&
            observedAtEpochSeconds >= 0 &&
            nowEpochSeconds >= observedAtEpochSeconds &&
            nowEpochSeconds - observedAtEpochSeconds <= ttlSeconds

    fun confidence(nowEpochSeconds: Long): CallerIdentityConfidence {
        if (!isFresh(nowEpochSeconds)) return CallerIdentityConfidence.UNKNOWN
        return when (kind) {
            CallerIdentityEvidenceKind.LOCAL_CONTACT,
            CallerIdentityEvidenceKind.AUTHORITATIVE_DIRECTORY -> CallerIdentityConfidence.VERIFIED
            CallerIdentityEvidenceKind.SIGNED_IDENTITY_PROOF ->
                if (proofId.isNullOrBlank()) CallerIdentityConfidence.INDICATIVE
                else CallerIdentityConfidence.VERIFIED
            CallerIdentityEvidenceKind.LICENSED_DIRECTORY -> CallerIdentityConfidence.CORROBORATED
            CallerIdentityEvidenceKind.COMMUNITY_REPORT,
            CallerIdentityEvidenceKind.HEURISTIC -> CallerIdentityConfidence.INDICATIVE
        }
    }
}

data class FusedCallerIdentity(
    val displayName: String?,
    val organisation: String?,
    val source: String?,
    val confidence: CallerIdentityConfidence,
    val evidenceCount: Int
)

object CallerIdentityFusionPolicy {
    private val confidenceRank = mapOf(
        CallerIdentityConfidence.UNKNOWN to 0,
        CallerIdentityConfidence.INDICATIVE to 1,
        CallerIdentityConfidence.CORROBORATED to 2,
        CallerIdentityConfidence.VERIFIED to 3
    )

    fun fuse(evidence: List<CallerIdentityEvidence>, nowEpochSeconds: Long): FusedCallerIdentity {
        val fresh = evidence.filter { it.isFresh(nowEpochSeconds) }
        val best = fresh
            .filter { !it.displayName.isNullOrBlank() || !it.organisation.isNullOrBlank() }
            .maxWithOrNull(
                compareBy<CallerIdentityEvidence> { confidenceRank.getValue(it.confidence(nowEpochSeconds)) }
                    .thenBy { it.observedAtEpochSeconds }
            )

        return FusedCallerIdentity(
            displayName = best?.displayName?.trim()?.takeIf { it.isNotEmpty() },
            organisation = best?.organisation?.trim()?.takeIf { it.isNotEmpty() },
            source = best?.source?.trim()?.takeIf { it.isNotEmpty() },
            confidence = best?.confidence(nowEpochSeconds) ?: CallerIdentityConfidence.UNKNOWN,
            evidenceCount = fresh.size
        )
    }
}
