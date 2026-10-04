package com.sentinel.quantum.security

import java.util.Locale

/**
 * Pure boundary for identifiers that leave the device or become globally shared keys.
 *
 * A national dial string has no globally unique meaning without a proven region. Callers must
 * canonicalize it at an Android boundary first. This policy never guesses a country and never
 * supplies a France fallback.
 */
internal object GlobalPhoneIdentityPolicy {
    fun canonicalE164OrNull(raw: String?): String? =
        CallRuleEngine.normalizeNumber(raw)?.takeIf { it.startsWith('+') }

    fun requireCanonicalE164(raw: String?): String =
        canonicalE164OrNull(raw)
            ?: throw IllegalArgumentException("E164 phone number required")

    fun canonicalRegionIsoOrNull(raw: String?): String? {
        val region = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
        return region.takeIf { it.length == 2 && it.all(Char::isLetter) }
    }

    fun requireRegionIso(raw: String?): String =
        canonicalRegionIsoOrNull(raw)
            ?: throw IllegalArgumentException("ISO region required")
}
