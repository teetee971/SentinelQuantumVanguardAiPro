package com.sentinel.quantum.security

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import java.util.Locale

/**
 * Offline national-format parser for a non-French home region.
 *
 * [warm] loads libphonenumber once, from application warm-up, never from the screening callback.
 * That library is the numbering plan for every region it publishes. Until it is loaded, parsing
 * and plan labels return null and the call rings. A national number is accepted only when the
 * plan considers it valid in that exact region. A plan label is not an operator, a location,
 * or a block.
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

    /** Zero until [warm]. The published plan covers every supported region, not a shortlist. */
    fun supportedRegionCount(): Int = util?.supportedRegions?.size ?: 0

    /**
     * Type and region from the numbering plan. Null when metadata is not loaded, the number is
     * not valid, or the type is unknown. This never decides to block or silence.
     */
    fun planLabel(e164: String): String? {
        val phoneUtil = util ?: return null
        if (!e164.startsWith("+")) return null
        val parsed = runCatching { phoneUtil.parse(e164, "ZZ") }.getOrNull() ?: return null
        if (!phoneUtil.isValidNumber(parsed)) return null
        val type = generalTypeLabel(phoneUtil.getNumberType(parsed)) ?: return null
        val region = phoneUtil.getRegionCodeForNumber(parsed)?.let(::regionName)
        return if (region.isNullOrBlank()) type else "$type · $region"
    }

    private fun generalTypeLabel(type: PhoneNumberType): String? = when (type) {
        PhoneNumberType.PREMIUM_RATE -> "Surtaxé"
        PhoneNumberType.TOLL_FREE -> "Numéro vert"
        PhoneNumberType.VOIP -> "VoIP"
        PhoneNumberType.SHARED_COST -> "Coût partagé"
        PhoneNumberType.MOBILE -> "Mobile"
        PhoneNumberType.FIXED_LINE -> "Fixe"
        PhoneNumberType.FIXED_LINE_OR_MOBILE -> "Fixe ou mobile"
        PhoneNumberType.PERSONAL_NUMBER -> "Numéro personnel"
        PhoneNumberType.PAGER -> "Radiomessagerie"
        PhoneNumberType.UAN -> "Numéro d'entreprise"
        PhoneNumberType.VOICEMAIL -> "Messagerie"
        else -> null
    }

    private fun regionName(code: String): String {
        val name = Locale("", code).getDisplayCountry(Locale.FRENCH).trim()
        if (name.isEmpty() || name.equals(code, ignoreCase = true)) return code
        return name
    }

    internal fun resetForTests() {
        util = null
    }
}
