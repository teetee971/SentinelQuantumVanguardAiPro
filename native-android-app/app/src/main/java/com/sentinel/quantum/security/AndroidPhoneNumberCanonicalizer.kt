package com.sentinel.quantum.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Android boundary for region-aware E.164 canonicalization.
 *
 * The pure [CallRuleEngine] deliberately refuses to invent a country for national numbers. This
 * adapter may promote a national representation to E.164 only when a region is explicit or
 * unambiguously observed from the selected/active SIMs. It never falls back to the UI locale,
 * a hard-coded FR region, or an arbitrary default SIM when observations disagree.
 */
class AndroidPhoneNumberCanonicalizer(context: Context) {
    private val appContext = context.applicationContext

    fun normalize(
        rawNumber: String?,
        subscriptionId: Int? = null,
        explicitRegionIso: String? = null
    ): String? {
        val regionIso = observedRegionIso(subscriptionId, explicitRegionIso)
        return normalizeWithKnownRegion(rawNumber, regionIso)
    }

    fun observedRegionIso(
        subscriptionId: Int? = null,
        explicitRegionIso: String? = null
    ): String? {
        sanitizeRegionIso(explicitRegionIso)?.let { return it }

        if (subscriptionId != null && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return observedRegionForSubscription(subscriptionId)
        }

        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val activeSubscriptionIds = runCatching {
            appContext.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList
                .orEmpty()
                .map { it.subscriptionId }
                .distinct()
        }.getOrNull() ?: return null

        if (activeSubscriptionIds.isEmpty()) return null
        return unambiguousRegion(
            activeSubscriptionIds.map(::observedRegionForSubscription)
        )
    }

    private fun observedRegionForSubscription(subscriptionId: Int): String? {
        val manager = telephonyManager(subscriptionId) ?: return null
        return resolveRegionIso(
            explicitRegionIso = null,
            simRegionIso = runCatching { manager.simCountryIso }.getOrNull(),
            networkRegionIso = runCatching { manager.networkCountryIso }.getOrNull()
        )
    }

    private fun telephonyManager(subscriptionId: Int): TelephonyManager? {
        val base = appContext.getSystemService(TelephonyManager::class.java) ?: return null
        return runCatching { base.createForSubscriptionId(subscriptionId) }.getOrNull()
    }

    companion object {
        private val ISO_REGION = Regex("[A-Z]{2}")

        /**
         * Fast canonicalization path for CallScreeningService: no subscription lookup, no I/O and
         * no country guess. A missing/ambiguous cached region leaves national syntax untouched.
         * Any representation returned with a leading plus is guaranteed to satisfy the shared
         * global E.164 identity contract.
         */
        fun normalizeWithKnownRegion(rawNumber: String?, regionIso: String?): String? {
            val syntaxOnly = CallRuleEngine.normalizeNumber(rawNumber) ?: return null
            if (syntaxOnly.startsWith('+')) {
                return GlobalPhoneIdentityPolicy.canonicalE164OrNull(syntaxOnly)
            }
            val safeRegion = sanitizeRegionIso(regionIso) ?: return syntaxOnly
            val e164 = runCatching {
                PhoneNumberUtils.formatNumberToE164(rawNumber.orEmpty(), safeRegion)
            }.getOrNull()
            GlobalPhoneIdentityPolicy.canonicalE164OrNull(e164)?.let { return it }

            // Android's platform formatter does not canonicalize Mayotte (YT) national forms on
            // all supported API levels. This fallback is deliberately territory-scoped: it is
            // reachable only after an explicit or unambiguous YT SIM/network observation, and it
            // accepts only Mayotte's known 0269 fixed-line / 0639 mobile national prefixes.
            if (safeRegion == "YT" && syntaxOnly.matches(Regex("0(?:269|639)\\d{6}"))) {
                return GlobalPhoneIdentityPolicy.canonicalE164OrNull("+262" + syntaxOnly.drop(1))
            }
            return syntaxOnly
        }

        /**
         * Pure rule for one known subscription. An explicit caller-supplied region wins. Without
         * one, SIM and network observations may fill a missing value but may never override each
         * other: two valid conflicting regions are ambiguous and therefore fail closed.
         */
        fun resolveRegionIso(
            explicitRegionIso: String?,
            simRegionIso: String?,
            networkRegionIso: String?
        ): String? {
            sanitizeRegionIso(explicitRegionIso)?.let { return it }
            val sim = sanitizeRegionIso(simRegionIso)
            val network = sanitizeRegionIso(networkRegionIso)
            return when {
                sim != null && network != null && sim != network -> null
                sim != null -> sim
                else -> network
            }
        }

        /**
         * Multi-SIM safety rule: national canonicalization is allowed only when every active
         * subscription has a usable ISO region and all observations agree. One missing/malformed
         * observation is enough to keep the region unknown because another active SIM could belong
         * to a different numbering plan.
         */
        fun unambiguousRegion(observedRegions: Collection<String?>): String? {
            if (observedRegions.isEmpty()) return null
            val regions = linkedSetOf<String>()
            for (observed in observedRegions) {
                val region = sanitizeRegionIso(observed) ?: return null
                regions += region
                if (regions.size > 1) return null
            }
            return regions.singleOrNull()
        }

        private fun sanitizeRegionIso(value: String?): String? {
            val normalized = value?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return normalized.takeIf(ISO_REGION::matches)
        }
    }
}
