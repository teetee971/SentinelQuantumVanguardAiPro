package com.sentinel.quantum.security

/**
 * Conservative emergency-call guard.
 *
 * This is intentionally not a hard-coded country-number catalogue.
 * Android's platform emergency-number determination remains authoritative;
 * this model only ensures that a platform-confirmed emergency call bypasses
 * ordinary Sentinel anti-fraud actions.
 */
object EmergencyCallGuard {
    enum class RequestedAction { ALLOW, WARN, SILENCE, BLOCK, CONFIRM_CALLBACK }

    data class Result(
        val action: RequestedAction,
        val bypassedOrdinaryProtection: Boolean,
        val reason: String
    )

    fun requiresExplicitPhoneAccountSelection(platformConfirmsEmergency: Boolean): Boolean =
        !platformConfirmsEmergency

    fun apply(platformConfirmsEmergency: Boolean, requested: RequestedAction): Result {
        if (!platformConfirmsEmergency) {
            return Result(requested, false, "ORDINARY_PHONE_POLICY")
        }
        return Result(
            action = RequestedAction.ALLOW,
            bypassedOrdinaryProtection = requested != RequestedAction.ALLOW,
            reason = "PLATFORM_CONFIRMED_EMERGENCY"
        )
    }
}
