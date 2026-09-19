package com.sentinel.quantum.security

/**
 * Voluntary local safety profile. It never enables covert monitoring or remote control.
 */
object FamilySafetyPolicy {
    enum class Profile { STANDARD, ASSISTED }
    enum class Risk { NONE, UNKNOWN_CALLER, PREMIUM_RATE_CALLBACK, WANGIRI_CALLBACK }
    enum class Action { ALLOW, WARN, REQUIRE_CONFIRMATION }

    data class Context(
        val profile: Profile,
        val risk: Risk,
        val platformEmergency: Boolean
    )

    data class Decision(val action: Action, val reason: String)

    fun decide(c: Context): Decision = when {
        c.platformEmergency -> Decision(Action.ALLOW, "EMERGENCY")
        c.profile == Profile.STANDARD -> when (c.risk) {
            Risk.NONE -> Decision(Action.ALLOW, "NO_SIGNAL")
            else -> Decision(Action.WARN, "STANDARD_WARNING")
        }
        c.risk == Risk.PREMIUM_RATE_CALLBACK -> Decision(Action.REQUIRE_CONFIRMATION, "PREMIUM_RATE_CONFIRMATION")
        c.risk == Risk.WANGIRI_CALLBACK -> Decision(Action.REQUIRE_CONFIRMATION, "WANGIRI_CONFIRMATION")
        c.risk == Risk.UNKNOWN_CALLER -> Decision(Action.WARN, "UNKNOWN_CALLER_WARNING")
        else -> Decision(Action.ALLOW, "NO_SIGNAL")
    }
}
