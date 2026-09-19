package com.sentinel.quantum.security

/**
 * Voluntary local-only safeguards for users who want stronger call warnings.
 * This policy does not provide remote supervision, covert monitoring or account control.
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

    fun decide(context: Context): Action {
        if (context.platformEmergency) return Action.ALLOW
        if (context.profile == Profile.STANDARD) return Action.ALLOW
        return when (context.risk) {
            Risk.NONE -> Action.ALLOW
            Risk.UNKNOWN_CALLER -> Action.WARN
            Risk.PREMIUM_RATE_CALLBACK,
            Risk.WANGIRI_CALLBACK -> Action.REQUIRE_CONFIRMATION
        }
    }
}
