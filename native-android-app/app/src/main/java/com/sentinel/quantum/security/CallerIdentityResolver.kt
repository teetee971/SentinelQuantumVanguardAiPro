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
        val compact = rawNumber.orEmpty().trim().filter { it.isDigit() || it == '+' }
        if (compact.isEmpty()) return ""
        val international = when {
            compact.startsWith("+") -> "+" + compact.drop(1).filter(Char::isDigit)
            compact.startsWith("00") -> "+" + compact.drop(2).filter(Char::isDigit)
            compact.length == 10 && compact.startsWith('0') -> "+33" + compact.drop(1)
            else -> compact.filter(Char::isDigit)
        }
        return international.take(32)
    }

    private fun classifyType(number: String): String {
        val french = when {
            number.startsWith("+33") -> "0" + number.drop(3)
            number.length == 10 && number.startsWith('0') -> number
            else -> null
        }
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
