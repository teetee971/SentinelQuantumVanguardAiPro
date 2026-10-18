package com.sentinel.quantum.security

/**
 * Final safety boundary between rule evaluation and Telecom enforcement.
 *
 * A non-user rule must never reject an incoming call. Until on-device opt-in
 * and physical-call qualification exist, automatic reputation SILENCE is also
 * disabled: silencing can cause the same missed-call symptom as rejection.
 * Unexpected decisions fail open.
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
            CallRuleEngine.Action.SILENCE -> CallRuleEngine.Action.ALLOW
            CallRuleEngine.Action.ALLOW -> CallRuleEngine.Action.ALLOW
        }
}
