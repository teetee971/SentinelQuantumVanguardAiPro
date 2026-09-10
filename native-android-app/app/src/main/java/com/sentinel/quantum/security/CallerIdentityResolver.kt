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

    private val countries = listOf(
        Country("+590", "GP", "Guadeloupe", "🇬🇵"),
        Country("+594", "GF", "Guyane française", "🇬🇫"),
        Country("+596", "MQ", "Martinique", "🇲🇶"),
        Country("+262", "RE", "La Réunion / Mayotte", "🇷🇪"),
        Country("+377", "MC", "Monaco", "🇲🇨"),
        Country("+352", "LU", "Luxembourg", "🇱🇺"),
        Country("+351", "PT", "Portugal", "🇵🇹"),
        Country("+353", "IE", "Irlande", "🇮🇪"),
        Country("+358", "FI", "Finlande", "🇫🇮"),
        Country("+380", "UA", "Ukraine", "🇺🇦"),
        Country("+420", "CZ", "Tchéquie", "🇨🇿"),
        Country("+421", "SK", "Slovaquie", "🇸🇰"),
        Country("+212", "MA", "Maroc", "🇲🇦"),
        Country("+213", "DZ", "Algérie", "🇩🇿"),
        Country("+216", "TN", "Tunisie", "🇹🇳"),
        Country("+221", "SN", "Sénégal", "🇸🇳"),
        Country("+225", "CI", "Côte d’Ivoire", "🇨🇮"),
        Country("+237", "CM", "Cameroun", "🇨🇲"),
        Country("+243", "CD", "Rép. démocratique du Congo", "🇨🇩"),
        Country("+971", "AE", "Émirats arabes unis", "🇦🇪"),
        Country("+972", "IL", "Israël", "🇮🇱"),
        Country("+974", "QA", "Qatar", "🇶🇦"),
        Country("+33", "FR", "France", "🇫🇷"),
        Country("+32", "BE", "Belgique", "🇧🇪"),
        Country("+34", "ES", "Espagne", "🇪🇸"),
        Country("+39", "IT", "Italie", "🇮🇹"),
        Country("+41", "CH", "Suisse", "🇨🇭"),
        Country("+44", "GB", "Royaume-Uni", "🇬🇧"),
        Country("+49", "DE", "Allemagne", "🇩🇪"),
        Country("+31", "NL", "Pays-Bas", "🇳🇱"),
        Country("+43", "AT", "Autriche", "🇦🇹"),
        Country("+45", "DK", "Danemark", "🇩🇰"),
        Country("+46", "SE", "Suède", "🇸🇪"),
        Country("+47", "NO", "Norvège", "🇳🇴"),
        Country("+48", "PL", "Pologne", "🇵🇱"),
        Country("+30", "GR", "Grèce", "🇬🇷"),
        Country("+40", "RO", "Roumanie", "🇷🇴"),
        Country("+55", "BR", "Brésil", "🇧🇷"),
        Country("+52", "MX", "Mexique", "🇲🇽"),
        Country("+54", "AR", "Argentine", "🇦🇷"),
        Country("+61", "AU", "Australie", "🇦🇺"),
        Country("+64", "NZ", "Nouvelle-Zélande", "🇳🇿"),
        Country("+81", "JP", "Japon", "🇯🇵"),
        Country("+82", "KR", "Corée du Sud", "🇰🇷"),
        Country("+86", "CN", "Chine", "🇨🇳"),
        Country("+91", "IN", "Inde", "🇮🇳"),
        Country("+7", "RU", "Russie / Kazakhstan", "🌐"),
        Country("+1", "NANP", "États-Unis / Canada / Caraïbes", "🌎")
    ).sortedByDescending { it.callingCode.length }

    fun resolve(
        rawNumber: String?,
        verification: String,
        displayName: String? = null,
        organisation: String? = null,
        identitySource: String? = null,
        identityVerified: Boolean = false
    ): Profile {
        val normalized = normalize(rawNumber)
        val country = countries.firstOrNull { normalized.startsWith(it.callingCode) }
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
