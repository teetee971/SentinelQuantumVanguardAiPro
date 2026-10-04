package com.sentinel.quantum.security

/**
 * Routes official numbering lookups only after a phone identity is globally unambiguous.
 * Country-specific directories must never be selected from UI locale or from national syntax.
 */
internal object PhoneDirectoryRoutingPolicy {
    enum class Target { ARCEP, RTR, NONE }

    fun targetFor(rawNumber: String?): Target {
        val e164 = GlobalPhoneIdentityPolicy.canonicalE164OrNull(rawNumber) ?: return Target.NONE
        return when {
            RtrDirectoryClient.normalize(e164) != null -> Target.RTR
            ArcepDirectoryClient.toFrenchNational(e164) != null -> Target.ARCEP
            else -> Target.NONE
        }
    }
}
