package com.sentinel.quantum.security

import java.util.Locale

/**
 * Stable fraud vocabulary shared by Phone Core call/SMS presentation.
 *
 * The taxonomy is descriptive only: mapping a signal to a category never blocks a call.
 * CallRuleEngine remains the sole call-screening policy authority.
 */
enum class PhoneFraudCategory(
    val frenchLabel: String,
    val appliesToCalls: Boolean,
    val appliesToMessages: Boolean
) {
    TELEMARKETING("Démarchage téléphonique", true, false),
    ROBOCALL("Appel automatisé", true, false),
    WANGIRI("Wangiri / appel très court", true, false),
    SPOOFING("Usurpation du numéro", true, true),
    PREMIUM_RATE("Numéro surtaxé", true, true),
    BANK_IMPERSONATION("Faux conseiller bancaire", true, true),
    DELIVERY_SCAM("Fausse livraison / faux colis", true, true),
    TECH_SUPPORT_SCAM("Faux support technique", true, true),
    GOVERNMENT_IMPERSONATION("Usurpation d’administration", true, true),
    PHISHING_LINK("Lien de phishing", false, true),
    HARASSMENT("Harcèlement", true, true),
    OTHER("Autre signalement", true, true)
}

object PhoneFraudTaxonomy {
    fun fromSignal(raw: String): PhoneFraudCategory? {
        val signal = raw.trim()
            .take(MAX_SIGNAL_CHARS)
            .uppercase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_')
        return when (signal) {
            "TELEMARKETING", "SOLICITATION", "COLD_CALL" -> PhoneFraudCategory.TELEMARKETING
            "ROBOCALL", "AUTOMATED_CALL" -> PhoneFraudCategory.ROBOCALL
            "WANGIRI", "SHORT_RING" -> PhoneFraudCategory.WANGIRI
            "SPOOFING", "CALLER_ID_SPOOFING", "VERIFICATION_FAILED" -> PhoneFraudCategory.SPOOFING
            "PREMIUM_RATE", "SURCHARGED_NUMBER" -> PhoneFraudCategory.PREMIUM_RATE
            "BANK_IMPERSONATION", "FAKE_BANK", "BANK_SCAM" -> PhoneFraudCategory.BANK_IMPERSONATION
            "DELIVERY_SCAM", "PARCEL_SCAM", "FAKE_DELIVERY" -> PhoneFraudCategory.DELIVERY_SCAM
            "TECH_SUPPORT_SCAM", "FAKE_SUPPORT" -> PhoneFraudCategory.TECH_SUPPORT_SCAM
            "GOVERNMENT_IMPERSONATION", "FAKE_GOVERNMENT" -> PhoneFraudCategory.GOVERNMENT_IMPERSONATION
            "PHISHING", "PHISHING_LINK", "MALICIOUS_LINK" -> PhoneFraudCategory.PHISHING_LINK
            "HARASSMENT", "ABUSIVE_CALL" -> PhoneFraudCategory.HARASSMENT
            "OTHER" -> PhoneFraudCategory.OTHER
            else -> null
        }
    }

    /**
     * The public reporting backend currently supports a narrower wire taxonomy.
     * Unsupported descriptive categories are deliberately sent as OTHER rather than invented.
     */
    fun toCommunityCategory(category: PhoneFraudCategory): CommunityReportClient.Category =
        when (category) {
            PhoneFraudCategory.WANGIRI -> CommunityReportClient.Category.WANGIRI
            PhoneFraudCategory.SPOOFING -> CommunityReportClient.Category.SPOOFING
            PhoneFraudCategory.PREMIUM_RATE -> CommunityReportClient.Category.PREMIUM_RATE
            PhoneFraudCategory.ROBOCALL -> CommunityReportClient.Category.ROBOCALL
            PhoneFraudCategory.TELEMARKETING -> CommunityReportClient.Category.TELEMARKETING
            PhoneFraudCategory.BANK_IMPERSONATION -> CommunityReportClient.Category.BANK_IMPERSONATION
            PhoneFraudCategory.DELIVERY_SCAM -> CommunityReportClient.Category.DELIVERY_SCAM
            PhoneFraudCategory.TECH_SUPPORT_SCAM -> CommunityReportClient.Category.TECH_SUPPORT_SCAM
            PhoneFraudCategory.GOVERNMENT_IMPERSONATION -> CommunityReportClient.Category.GOVERNMENT_IMPERSONATION
            PhoneFraudCategory.HARASSMENT -> CommunityReportClient.Category.HARASSMENT
            PhoneFraudCategory.PHISHING_LINK,
            PhoneFraudCategory.OTHER -> CommunityReportClient.Category.OTHER
        }

    private const val MAX_SIGNAL_CHARS = 64
}
