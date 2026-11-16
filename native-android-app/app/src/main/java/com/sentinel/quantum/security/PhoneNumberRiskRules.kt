package com.sentinel.quantum.security

/**
 * Small, local ruleset for number patterns that deserve explicit callback caution.
 *
 * This is not a reputation database and does not claim that every matching number is fraudulent.
 */
object PhoneNumberRiskRules {
    private val premiumRatePrefixes = setOf(
        "+1900",
        "+33897",
        "+33899"
    )

    fun isKnownPremiumRatePrefix(rawNumber: String): Boolean {
        val normalized = CallRuleEngine.normalizeNumber(rawNumber) ?: return false
        return premiumRatePrefixes.any(normalized::startsWith)
    }

    fun assistedRisk(rawNumber: String): FamilySafetyPolicy.Risk =
        if (isKnownPremiumRatePrefix(rawNumber)) {
            FamilySafetyPolicy.Risk.PREMIUM_RATE_CALLBACK
        } else {
            FamilySafetyPolicy.Risk.NONE
        }
}
