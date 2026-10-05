package com.sentinel.quantum.security

/**
 * Deterministic, offline caller-ID facts safe to compute on the incoming-call path.
 * Names and organisations are deliberately supplied by a separate trusted local source.
 *
 * This resolver does not own regional canonicalization. It consumes explicit international E.164
 * when available, or a region-neutral national representation when the Android boundary could not
 * prove a country. That keeps presentation from silently reintroducing `0… -> +33…` semantics.
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

    fun normalize(rawNumber: String?): String =
        CallRuleEngine.normalizeNumber(rawNumber).orEmpty()

    private fun classifyType(number: String): String {
        // Type classification is only asserted where the country is explicit in E.164. A national
        // 06/08/09 sequence with unknown region is not called French merely because it looks French.
        val french = number.takeIf { it.startsWith("+33") }?.let { "0" + it.drop(3) }
        return when {
            french?.startsWith("06") == true || french?.startsWith("07") == true -> "Mobile"
            french?.startsWith("01") == true || french?.startsWith("02") == true ||
                french?.startsWith("03") == true || french?.startsWith("04") == true ||
                french?.startsWith("05") == true -> "Fixe ou polyvalent"
            french?.startsWith("08") == true -> "Service / tarification à vérifier"
            french?.startsWith("09") == true -> "Polyvalent / voix sur IP"
            number.startsWith('+') -> "International"
            else -> "Type indéterminé"
        }
    }
}
