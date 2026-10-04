package com.sentinel.quantum.security

/**
 * Presentation-only identity policy for phone favorites.
 *
 * A national representation is promoted to E.164 only when Android supplied an explicit or
 * unambiguous region. Without that evidence, the national syntax is retained instead of inventing
 * a country. Explicit international forms remain canonical E.164 identities.
 */
object PhoneFavoriteIdentityPolicy {
    fun normalize(rawNumber: String?, observedRegionIso: String?): String? =
        AndroidPhoneNumberCanonicalizer.normalizeWithKnownRegion(rawNumber, observedRegionIso)
}
