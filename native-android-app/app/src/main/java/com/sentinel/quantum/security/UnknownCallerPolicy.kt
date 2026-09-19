package com.sentinel.quantum.security

/**
 * Deterministic local policy for optional unknown-caller protection.
 * Emergency status must be supplied from Android/platform determination.
 */
object UnknownCallerPolicy {
    enum class Action { ALLOW, SILENCE }

    data class Context(
        val modeEnabled: Boolean,
        val platformEmergency: Boolean,
        val inContacts: Boolean,
        val favorite: Boolean,
        val explicitlyAllowed: Boolean,
        val repeatCall: Boolean
    )

    data class Decision(val action: Action, val reason: String)

    fun decide(c: Context): Decision = when {
        c.platformEmergency -> Decision(Action.ALLOW, "EMERGENCY")
        !c.modeEnabled -> Decision(Action.ALLOW, "MODE_DISABLED")
        c.explicitlyAllowed -> Decision(Action.ALLOW, "ALLOWLIST")
        c.favorite -> Decision(Action.ALLOW, "FAVORITE")
        c.inContacts -> Decision(Action.ALLOW, "CONTACT")
        c.repeatCall -> Decision(Action.ALLOW, "REPEAT_CALL")
        else -> Decision(Action.SILENCE, "UNKNOWN_CALLER")
    }
}
