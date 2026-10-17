package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.CommunityReportClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CommunityReportEvidenceAdapterTest {
    @Test
    fun pending_report_is_context_only_and_has_no_score_or_raw_number() {
        val evidence = CommunityReportEvidenceAdapter.from(
            category = CommunityReportClient.Category.BANK_IMPERSONATION,
            result = CommunityReportClient.Result(
                status = "pending",
                effectOnReputation = "pending"
            )
        )

        assertEquals("BANK_IMPERSONATION", evidence.categoryCode)
        assertEquals(
            IntelligenceEvidence.CommunityModerationState.PENDING,
            evidence.moderationState
        )
        assertEquals(
            IntelligenceEvidence.CommunityReputationEffect.PENDING,
            evidence.effectOnReputation
        )
        assertFalse(evidence.toString().contains("+33612345678"))
    }

    @Test
    fun unknown_remote_status_remains_unknown() {
        val evidence = CommunityReportEvidenceAdapter.from(
            category = CommunityReportClient.Category.OTHER,
            result = CommunityReportClient.Result(
                status = "unexpected",
                effectOnReputation = "unexpected"
            )
        )

        assertEquals(IntelligenceEvidence.CommunityModerationState.UNKNOWN, evidence.moderationState)
        assertEquals(IntelligenceEvidence.CommunityReputationEffect.UNKNOWN, evidence.effectOnReputation)
    }
}
