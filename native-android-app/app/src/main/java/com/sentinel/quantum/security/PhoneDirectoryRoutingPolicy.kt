package com.sentinel.quantum.security

/**
 * Routes official numbering lookups only after the relevant numbering-plan context is trustworthy.
 * Full telephone identities require canonical E.164. ARCEP short codes are the only exception and
 * are accepted only when an observed telephony region belongs to the ARCEP-managed French plan.
 * UI locale, display language and hard-coded country defaults are never region evidence.
 */
internal object PhoneDirectoryRoutingPolicy {
    enum class Target { ARCEP, RTR, NONE }

    private val ARCEP_REGIONS = setOf("FR", "GP", "BL", "MF", "GF", "MQ", "RE", "YT", "PM")
    private val ARCEP_SHORT_CODE = Regex("\\d{4,6}")

    fun targetFor(rawNumber: String?, observedRegionIso: String? = null): Target {
        val e164 = GlobalPhoneIdentityPolicy.canonicalE164OrNull(rawNumber)
        if (e164 != null) {
            return when {
                RtrDirectoryClient.normalize(e164) != null -> Target.RTR
                ArcepDirectoryClient.toFrenchNational(e164) != null -> Target.ARCEP
                else -> Target.NONE
            }
        }

        val region = GlobalPhoneIdentityPolicy.canonicalRegionIsoOrNull(observedRegionIso)
        if (region !in ARCEP_REGIONS) return Target.NONE

        val compact = rawNumber?.trim()?.replace(Regex("[\\s().-]"), "") ?: return Target.NONE
        return if (
            ARCEP_SHORT_CODE.matches(compact) &&
            ArcepDirectoryClient.toFrenchNational(compact) != null
        ) {
            Target.ARCEP
        } else {
            Target.NONE
        }
    }
}
