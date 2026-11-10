package com.sentinel.quantum.security

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType

/**
 * Offline national-format parser for a non-French home region.
 *
 * The metadata is loaded only by [warm], from the application warm-up, never from the screening
 * callback. Until that finishes, [toE164] returns null and the call rings. A number is accepted
 * only when libphonenumber considers it valid in that exact region.
 */
object InternationalNumberParser {
    @Volatile
    private var util: PhoneNumberUtil? = null

    fun warm() {
        if (util != null) return
        util = PhoneNumberUtil.getInstance()
    }

    fun toE164(raw: String, region: String): String? {
        val phoneUtil = util ?: return null
        val home = region.trim().uppercase()
        if (home.length != 2 || home !in phoneUtil.supportedRegions) return null
        val parsed = runCatching { phoneUtil.parse(raw, home) }.getOrNull() ?: return null
        if (!phoneUtil.isValidNumberForRegion(parsed, home)) return null
        val formatted = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        if (!formatted.startsWith("+") || formatted.count(Char::isDigit) !in 7..15) return null
        return formatted
    }

    /** Display-only tariff label. Null means "no extra label", never a block. */
    fun tariffLabel(e164: String): String? {
        val phoneUtil = util ?: return null
        if (!e164.startsWith("+")) return null
        val parsed = runCatching { phoneUtil.parse(e164, "ZZ") }.getOrNull() ?: return null
        return when (phoneUtil.getNumberType(parsed)) {
            PhoneNumberType.PREMIUM_RATE -> "Surtaxé"
            PhoneNumberType.TOLL_FREE -> "Numéro vert"
            PhoneNumberType.VOIP -> "VoIP"
            PhoneNumberType.SHARED_COST -> "Coût partagé"
            else -> null
        }
    }

    /**
     * True only when metadata is already loaded and the number is a premium-rate type.
     * False when metadata is not ready, so screening fails open instead of guessing.
     */
    fun isPremiumRate(e164: String): Boolean {
        val phoneUtil = util ?: return false
        if (!e164.startsWith("+")) return false
        val parsed = runCatching { phoneUtil.parse(e164, "ZZ") }.getOrNull() ?: return false
        return phoneUtil.getNumberType(parsed) == PhoneNumberType.PREMIUM_RATE
    }

    internal fun resetForTests() {
        util = null
    }
}
