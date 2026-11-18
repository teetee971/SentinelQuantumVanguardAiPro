package com.sentinel.quantum.security

internal enum class CallScreeningPlatformEffect {
    /** No CallResponse restriction. The incoming call rings. */
    ALLOW,

    /** [android.telecom.CallScreeningService.CallResponse.Builder.setSilenceCall], API 29+. */
    SILENCE,

    /** setDisallowCall and setRejectCall. The call is rejected, not merely silenced. */
    REJECT
}

/** setSilenceCall exists from Android 10. Earlier platform levels cannot silence. */
internal const val SILENCE_CALL_MIN_SDK = 29

internal fun callScreeningPlatformEffect(
    action: CallRuleEngine.Action,
    sdkInt: Int
): CallScreeningPlatformEffect = when (action) {
    CallRuleEngine.Action.BLOCK -> CallScreeningPlatformEffect.REJECT
    CallRuleEngine.Action.SILENCE ->
        if (sdkInt >= SILENCE_CALL_MIN_SDK) CallScreeningPlatformEffect.SILENCE
        else CallScreeningPlatformEffect.ALLOW
    CallRuleEngine.Action.ALLOW -> CallScreeningPlatformEffect.ALLOW
}

/**
 * Emergency classification is fail-open. A null or true result must not reach block or silence.
 * Only a strict false is allowed to continue into the rule engine.
 */
internal fun screeningMustFailOpen(emergencyClassification: Boolean?): Boolean =
    emergencyClassification != false

/**
 * What Android will actually do, and therefore the only action the log and the caller card may show.
 * Silence requested below Android 10 becomes an allow: the call rings.
 */
internal data class ProjectedScreeningDecision(
    val action: CallRuleEngine.Action,
    val reason: String,
    val effect: CallScreeningPlatformEffect
)

internal fun projectScreeningDecision(
    decision: CallRuleEngine.Decision,
    sdkInt: Int
): ProjectedScreeningDecision {
    val effect = callScreeningPlatformEffect(decision.action, sdkInt)
    return when {
        decision.action == CallRuleEngine.Action.SILENCE &&
            effect == CallScreeningPlatformEffect.ALLOW ->
            ProjectedScreeningDecision(
                action = CallRuleEngine.Action.ALLOW,
                reason = "SILENCE_UNSUPPORTED",
                effect = effect
            )
        effect == CallScreeningPlatformEffect.REJECT ->
            ProjectedScreeningDecision(CallRuleEngine.Action.BLOCK, decision.reason, effect)
        effect == CallScreeningPlatformEffect.SILENCE ->
            ProjectedScreeningDecision(CallRuleEngine.Action.SILENCE, decision.reason, effect)
        else -> ProjectedScreeningDecision(CallRuleEngine.Action.ALLOW, decision.reason, effect)
    }
}
