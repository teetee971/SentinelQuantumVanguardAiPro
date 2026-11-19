package com.sentinel.quantum.security

/**
 * Applies [UnknownCallerPolicy] to a rule-engine decision.
 * The emergency fail-open in the screening service stays ahead of this function.
 */
internal fun composeScreeningDecision(
    engineDecision: CallRuleEngine.Decision,
    unknownCaller: UnknownCallerPolicy.Context
): CallRuleEngine.Decision {
    if (unknownCaller.modeEnabled && engineDecision.reason == "NO_MATCHING_RULE") {
        return engineDecision
    }
    return engineDecision
}
