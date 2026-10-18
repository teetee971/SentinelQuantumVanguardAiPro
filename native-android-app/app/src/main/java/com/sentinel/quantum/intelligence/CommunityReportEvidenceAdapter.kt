package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.CommunityReportClient

/** Maps an explicit community response into bounded context without creating a risk score. */
object CommunityReportEvidenceAdapter {
    fun from(
        category: CommunityReportClient.Category,
        result: CommunityReportClient.Result
    ): IntelligenceEvidence.CommunityReport = IntelligenceEvidence.CommunityReport(
        categoryCode = category.name,
        moderationState = moderationState(result.status),
        effectOnReputation = reputationEffect(result.effectOnReputation)
    )

    private fun moderationState(status: String): IntelligenceEvidence.CommunityModerationState =
        when (status.trim().lowercase()) {
            "pending" -> IntelligenceEvidence.CommunityModerationState.PENDING
            "duplicate" -> IntelligenceEvidence.CommunityModerationState.DUPLICATE
            "accepted", "approved" -> IntelligenceEvidence.CommunityModerationState.ACCEPTED
            "rejected", "declined" -> IntelligenceEvidence.CommunityModerationState.REJECTED
            else -> IntelligenceEvidence.CommunityModerationState.UNKNOWN
        }

    private fun reputationEffect(effect: String): IntelligenceEvidence.CommunityReputationEffect =
        when (effect.trim().lowercase()) {
            "unchanged", "none", "no_change" ->
                IntelligenceEvidence.CommunityReputationEffect.UNCHANGED
            "pending" -> IntelligenceEvidence.CommunityReputationEffect.PENDING
            "updated", "applied" -> IntelligenceEvidence.CommunityReputationEffect.UPDATED
            else -> IntelligenceEvidence.CommunityReputationEffect.UNKNOWN
        }
}
