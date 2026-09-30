package com.sentinel.quantum.security

/**
 * Truthful local trust indicator for an active incoming or outgoing call.
 *
 * This is deliberately not an identity-verification system. A saved contact can raise familiarity,
 * while explicit local blocking or signed vigilance rules lower confidence. Absence of evidence
 * remains UNKNOWN rather than being rendered as "safe".
 */
object CallTrustIndicator {
    enum class Level {
        UNKNOWN,
        INDICATIVE,
        CAUTION,
        HIGH_RISK
    }

    data class Input(
        val contactKnown: Boolean,
        val localDecision: CallRuleEngine.Decision?,
        val premiumRateCaution: Boolean
    )

    data class Result(
        val level: Level,
        val title: String,
        val detail: String
    )

    fun assess(input: Input): Result {
        val decision = input.localDecision
        return when {
            decision?.action == CallRuleEngine.Action.BLOCK -> Result(
                level = Level.HIGH_RISK,
                title = "Confiance faible",
                detail = "Une règle locale explicite correspond à ce numéro."
            )
            decision?.action == CallRuleEngine.Action.SILENCE -> Result(
                level = Level.CAUTION,
                title = "Vigilance renforcée",
                detail = "Une règle de vigilance signée correspond à ce numéro."
            )
            input.premiumRateCaution -> Result(
                level = Level.CAUTION,
                title = "Vigilance renforcée",
                detail = "Préfixe potentiellement surtaxé ; vérifiez avant de rappeler."
            )
            input.contactKnown -> Result(
                level = Level.INDICATIVE,
                title = "Confiance indicative",
                detail = "Numéro présent dans votre répertoire local ; cela ne vérifie pas l’identité réelle de l’appelant."
            )
            else -> Result(
                level = Level.UNKNOWN,
                title = "Confiance non mesurée",
                detail = "Aucun signal local suffisant pour conclure que ce numéro est fiable ou frauduleux."
            )
        }
    }
}
