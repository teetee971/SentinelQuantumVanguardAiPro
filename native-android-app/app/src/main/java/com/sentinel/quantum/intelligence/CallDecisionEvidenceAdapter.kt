package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.CallRuleEngine

/** Pure projection from the existing call policy result into bounded intelligence evidence. */
object CallDecisionEvidenceAdapter {
    fun from(decision: CallRuleEngine.Decision): IntelligenceEvidence.CallDecision =
        IntelligenceEvidence.CallDecision(
            action = when (decision.action) {
                CallRuleEngine.Action.ALLOW -> IntelligenceEvidence.CallAction.ALLOW
                CallRuleEngine.Action.SILENCE -> IntelligenceEvidence.CallAction.SILENCE
                CallRuleEngine.Action.BLOCK -> IntelligenceEvidence.CallAction.BLOCK
            },
            ruleSource = decision.source.name,
            reasonCode = decision.reason,
            networkVerification = IntelligenceEvidence.NetworkVerification.NOT_VERIFIED
        )
}
