package com.sentinel.quantum.security

/**
 * Converts a call-screening result into the privacy-bounded Phone Core journal vocabulary.
 * The mapping deliberately excludes the raw/normalized phone number.
 */
object CallDecisionJournalMapper {
    fun toEntry(
        decision: CallRuleEngine.Decision,
        timestampMs: Long = System.currentTimeMillis()
    ): PhoneDecisionJournal.Entry {
        val action = when (decision.action) {
            CallRuleEngine.Action.ALLOW -> PhoneDecisionJournal.Action.ALLOW
            CallRuleEngine.Action.SILENCE -> PhoneDecisionJournal.Action.SILENCE
            CallRuleEngine.Action.BLOCK -> PhoneDecisionJournal.Action.BLOCK
        }
        val confidence = when (decision.source) {
            CallRuleEngine.RuleSource.USER -> SentinelConfidence.VERIFIED
            CallRuleEngine.RuleSource.SIGNED_REPUTATION -> SentinelConfidence.CORROBORATED
            CallRuleEngine.RuleSource.NONE -> SentinelConfidence.UNKNOWN
        }
        return PhoneDecisionJournal.Entry(
            timestampMs = timestampMs,
            action = action,
            ruleCode = decision.reason,
            confidence = confidence,
            provenance = decision.source.name,
            localOnly = true
        )
    }
}
