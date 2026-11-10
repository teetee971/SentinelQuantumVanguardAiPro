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
