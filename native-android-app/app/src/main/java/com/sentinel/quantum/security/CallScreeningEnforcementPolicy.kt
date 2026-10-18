package com.sentinel.quantum.security

/**
 * Final safety boundary between rule evaluation and Telecom enforcement.
 *
 * A non-user rule must never reject an incoming call. An invalid or future
 * decision provenance fails open rather than silently becoming a hard block.
 */
internal object CallScreeningEnforcementPolicy {
    fun actionFor(decision: CallRuleEngine.Decision): CallRuleEngine.Action =
        when (decision.action) {
            CallRuleEngine.Action.BLOCK ->
                if (decision.source == CallRuleEngine.RuleSource.USER) {
                    CallRuleEngine.Action.BLOCK
                } else {
                    CallRuleEngine.Action.ALLOW
                }
            CallRuleEngine.Action.SILENCE ->
                if (decision.source == CallRuleEngine.RuleSource.SIGNED_REPUTATION) {
                    CallRuleEngine.Action.SILENCE
                } else {
                    CallRuleEngine.Action.ALLOW
                }
            CallRuleEngine.Action.ALLOW -> CallRuleEngine.Action.ALLOW
        }
}
