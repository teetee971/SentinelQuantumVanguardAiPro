package com.sentinel.quantum.security

/**
 * Applies [UnknownCallerPolicy] after [CallRuleEngine].
 *
 * Emergency fail-open stays in the screening service and is not decided here.
 * An explicit block, a prefix or premium silence, an ambiguous line, or a
 * number that cannot be parsed is left untouched. Only an unmatched caller,
 * or the directory silence that means the same thing, takes the policy.
 */
internal fun composeScreeningDecision(
    engineDecision: CallRuleEngine.Decision,
    unknownCaller: UnknownCallerPolicy.Context
): CallRuleEngine.Decision {
    if (!unknownCaller.modeEnabled || unknownCaller.platformEmergency) return engineDecision
    if (engineDecision.action == CallRuleEngine.Action.BLOCK) return engineDecision
    if (engineDecision.reason != "NO_MATCHING_RULE" &&
        engineDecision.reason != "NOT_IN_DIRECTORY"
    ) {
        return engineDecision
    }
    val policy = UnknownCallerPolicy.decide(unknownCaller)
    val action = when (policy.action) {
        UnknownCallerPolicy.Action.ALLOW -> CallRuleEngine.Action.ALLOW
        UnknownCallerPolicy.Action.SILENCE -> CallRuleEngine.Action.SILENCE
    }
    return engineDecision.copy(
        action = action,
        reason = policy.reason,
        source = CallRuleEngine.RuleSource.USER
    )
}
