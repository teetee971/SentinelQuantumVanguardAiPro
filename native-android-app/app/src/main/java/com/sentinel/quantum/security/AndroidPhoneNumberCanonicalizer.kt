package com.sentinel.quantum.security

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Android boundary for region-aware E.164 canonicalization.
 *
 * The pure [CallRuleEngine] deliberately refuses to invent a country for national numbers. This
 * adapter may promote a national representation to E.164 only when a region is explicit or
 * unambiguously observed from the selected/active SIMs. It never falls back to the UI locale,
 * a hard-coded FR region, or an arbitrary default SIM when several active subscriptions disagree.
 */
class AndroidPhoneNumberCanonicalizer(context: Context) {
    private val appContext = context.applicationContext

    fun normalize(
        rawNumber: String?,
        subscriptionId: Int? = null,
        explicitRegionIso: String? = null
    ): String? {
        val syntaxOnly = CallRuleEngine.normalizeNumber(rawNumber) ?: return null
        if (syntaxOnly.startsWith('+')) return syntaxOnly

        val regionIso = observedRegionIso(subscriptionId, explicitRegionIso) ?: return syntaxOnly
        val e164 = runCatching {
            PhoneNumberUtils.formatNumberToE164(rawNumber.orEmpty(), regionIso)
        }.getOrNull()
        return CallRuleEngine.normalizeNumber(e164) ?: syntaxOnly
    }

    fun observedRegionIso(
        subscriptionId: Int? = null,
        explicitRegionIso: String? = null
    ): String? {
        sanitizeRegionIso(explicitRegionIso)?.let { return it }

        if (subscriptionId != null && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return observedRegionForSubscription(subscriptionId)
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
            activeSubscriptionIds.mapNotNull(::observedRegionForSubscription)
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

        /** Pure precedence rule for one known subscription. */
        fun resolveRegionIso(
            explicitRegionIso: String?,
            simRegionIso: String?,
            networkRegionIso: String?
        ): String? = sequenceOf(explicitRegionIso, simRegionIso, networkRegionIso)
            .mapNotNull(::sanitizeRegionIso)
            .firstOrNull()

        /**
         * Multi-SIM safety rule: national canonicalization is allowed only when every usable
         * active-subscription observation agrees on one ISO region.
         */
        fun unambiguousRegion(observedRegions: Collection<String>): String? {
            val regions = observedRegions.mapNotNull(::sanitizeRegionIso).distinct()
            return regions.singleOrNull()
        }

        private fun sanitizeRegionIso(value: String?): String? {
            val normalized = value?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return normalized.takeIf(ISO_REGION::matches)
        }
    }
}
