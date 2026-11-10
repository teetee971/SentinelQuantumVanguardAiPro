package com.sentinel.quantum.security

/**
 * Deterministic, offline caller-ID facts safe to compute on the incoming-call path.
 * Names and organisations are deliberately supplied by a separate trusted local source.
 */
object CallerIdentityResolver {
    data class Country(val callingCode: String, val isoCode: String, val name: String, val flag: String)

    data class Profile(
        val displayNumber: String,
        val countryName: String,
        val countryFlag: String,
        val countryIsoCode: String,
        val callType: String,
        val verification: String,
        val displayName: String? = null,
        val organisation: String? = null,
        val identitySource: String = "Numérotation internationale",
        val identityVerified: Boolean = false
    )

    fun resolve(
        rawNumber: String?,
        verification: String,
        displayName: String? = null,
        organisation: String? = null,
        identitySource: String? = null,
        identityVerified: Boolean = false
    ): Profile {
        val normalized = normalize(rawNumber)
        val country = E164CallingCodeDirectory.resolve(normalized)
        return Profile(
            displayNumber = normalized.ifBlank { "Numéro indisponible" },
            countryName = country?.name ?: "Pays indéterminé",
            countryFlag = country?.flag ?: "🌐",
            countryIsoCode = country?.isoCode ?: "ZZ",
            callType = classifyType(normalized),
            verification = verification,
            displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
            organisation = organisation?.trim()?.takeIf { it.isNotEmpty() },
            identitySource = identitySource?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Numérotation internationale",
            identityVerified = identityVerified
        )
    }

    fun normalize(rawNumber: String?): String {
        CallRuleEngine.normalizeNumber(rawNumber, ScreeningHomeRegion.current())?.let { return it }
        val compact = rawNumber.orEmpty().trim().filter { it.isDigit() || it == '+' }
        if (compact.isEmpty()) return ""
        return compact.take(32)
    }

    /**
     * Display label only. It is not a screening decision and must never be turned into a block.
     */
    private fun classifyType(number: String): String =
        CallRuleEngine.displayKind(number, ScreeningHomeRegion.current())
}
