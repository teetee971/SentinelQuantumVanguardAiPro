package com.sentinel.quantum.security

import java.util.Locale

/**
 * Combines one screening decision per installed SIM region.
 *
 * CallScreeningService does not receive the receiving line. When every installed
 * region yields the same identity, that decision is used. When only one region can
 * parse the digits, that identity is used. Two different valid numbers that disagree
 * fail open: a block on one parse must not reject the other line's caller, and a
 * contact on one parse is not silenced by the other.
 */
internal object ScreeningLineRegionPolicy {
    fun decide(
        regions: Set<String>,
        evaluate: (homeRegion: String) -> CallRuleEngine.Decision
    ): CallRuleEngine.Decision {
        val homes = regions.map { it.trim().uppercase(Locale.US) }.filter { it.length == 2 }.distinct()
        if (homes.size <= 1) return evaluate(homes.singleOrNull() ?: "FR")
        val decisions = homes.map(evaluate)
        val parsed = decisions.filter { !it.normalizedNumber.isNullOrBlank() }
        val identities = parsed.map { it.normalizedNumber }.distinct()
        if (identities.size <= 1) return parsed.firstOrNull() ?: decisions.first()
        if (parsed.all { it.action == CallRuleEngine.Action.BLOCK }) return parsed.first()
        parsed.firstOrNull { it.action == CallRuleEngine.Action.ALLOW && it.reason == "IN_DIRECTORY" }
            ?.let { return it }
        return CallRuleEngine.Decision(
            CallRuleEngine.Action.ALLOW,
            "AMBIGUOUS_LINE",
            null,
            CallRuleEngine.RuleSource.NONE
        )
    }
}
