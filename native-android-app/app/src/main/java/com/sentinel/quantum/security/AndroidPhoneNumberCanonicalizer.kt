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
 * observed from the selected SIM/network. It never falls back to the UI locale or hard-coded FR.
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

        val regionIso = resolveRegionIso(
            explicitRegionIso = explicitRegionIso,
            simRegionIso = observedSimRegionIso(subscriptionId),
            networkRegionIso = observedNetworkRegionIso(subscriptionId)
        ) ?: return syntaxOnly

        val e164 = runCatching {
            PhoneNumberUtils.formatNumberToE164(rawNumber.orEmpty(), regionIso)
        }.getOrNull()
        return CallRuleEngine.normalizeNumber(e164) ?: syntaxOnly
    }

    fun observedRegionIso(
        subscriptionId: Int? = null,
        explicitRegionIso: String? = null
    ): String? = resolveRegionIso(
        explicitRegionIso = explicitRegionIso,
        simRegionIso = observedSimRegionIso(subscriptionId),
        networkRegionIso = observedNetworkRegionIso(subscriptionId)
    )

    private fun observedSimRegionIso(subscriptionId: Int?): String? =
        telephonyManager(subscriptionId)?.let { manager ->
            runCatching { manager.simCountryIso }.getOrNull()
        }

    private fun observedNetworkRegionIso(subscriptionId: Int?): String? =
        telephonyManager(subscriptionId)?.let { manager ->
            runCatching { manager.networkCountryIso }.getOrNull()
        }

    private fun telephonyManager(subscriptionId: Int?): TelephonyManager? {
        val base = appContext.getSystemService(TelephonyManager::class.java) ?: return null
        if (subscriptionId == null || subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return base
        }
        return runCatching { base.createForSubscriptionId(subscriptionId) }.getOrNull() ?: base
    }

    companion object {
        private val ISO_REGION = Regex("[A-Z]{2}")

        /** Pure precedence rule, kept testable without Android framework calls. */
        fun resolveRegionIso(
            explicitRegionIso: String?,
            simRegionIso: String?,
            networkRegionIso: String?
        ): String? = sequenceOf(explicitRegionIso, simRegionIso, networkRegionIso)
            .mapNotNull(::sanitizeRegionIso)
            .firstOrNull()

        private fun sanitizeRegionIso(value: String?): String? {
            val normalized = value?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return normalized.takeIf(ISO_REGION::matches)
        }
    }
}
