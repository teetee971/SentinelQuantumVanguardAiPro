package com.sentinel.quantum.security

/**
 * Pure cross-module risk context shared by communication surfaces.
 *
 * This deliberately keeps each source's reason separate instead of collapsing evidence into an
 * opaque synthetic score. UNKNOWN never lowers a known signal, and a low signal never overrides a
 * stronger independent source.
 */
object UnifiedCommunicationRisk {
    enum class Level { UNKNOWN, LOW, MODERATE, HIGH }

    enum class Reason {
        SMS_CONTENT_MODERATE,
        SMS_CONTENT_HIGH,
        REPUTATION_MODERATE,
        REPUTATION_HIGH
    }

    data class Assessment(
        val level: Level,
        val smsLevel: SmsLinkAnalyzer.RiskLevel,
        val reputationBand: PhoneRiskCard.RiskBand,
        val reasons: List<Reason>
    )

    fun assess(
        sms: SmsLinkAnalyzer.Analysis?,
        reputation: PhoneRiskCard.Card?
    ): Assessment {
        val smsLevel = sms?.riskLevel ?: SmsLinkAnalyzer.RiskLevel.UNKNOWN
        val reputationBand = reputation?.riskBand ?: PhoneRiskCard.RiskBand.UNKNOWN
        val reasons = buildList {
            when (smsLevel) {
                SmsLinkAnalyzer.RiskLevel.HIGH -> add(Reason.SMS_CONTENT_HIGH)
                SmsLinkAnalyzer.RiskLevel.MEDIUM -> add(Reason.SMS_CONTENT_MODERATE)
                SmsLinkAnalyzer.RiskLevel.UNKNOWN,
                SmsLinkAnalyzer.RiskLevel.LOW -> Unit
            }
            when (reputationBand) {
                PhoneRiskCard.RiskBand.HIGH -> add(Reason.REPUTATION_HIGH)
                PhoneRiskCard.RiskBand.MODERATE -> add(Reason.REPUTATION_MODERATE)
                PhoneRiskCard.RiskBand.UNKNOWN,
                PhoneRiskCard.RiskBand.LOW -> Unit
            }
        }

        return Assessment(
            level = maxLevel(levelOf(smsLevel), levelOf(reputationBand)),
            smsLevel = smsLevel,
            reputationBand = reputationBand,
            reasons = reasons
        )
    }

    private fun levelOf(level: SmsLinkAnalyzer.RiskLevel): Level = when (level) {
        SmsLinkAnalyzer.RiskLevel.UNKNOWN -> Level.UNKNOWN
        SmsLinkAnalyzer.RiskLevel.LOW -> Level.LOW
        SmsLinkAnalyzer.RiskLevel.MEDIUM -> Level.MODERATE
        SmsLinkAnalyzer.RiskLevel.HIGH -> Level.HIGH
    }

    private fun levelOf(band: PhoneRiskCard.RiskBand): Level = when (band) {
        PhoneRiskCard.RiskBand.UNKNOWN -> Level.UNKNOWN
        PhoneRiskCard.RiskBand.LOW -> Level.LOW
        PhoneRiskCard.RiskBand.MODERATE -> Level.MODERATE
        PhoneRiskCard.RiskBand.HIGH -> Level.HIGH
    }

    private fun maxLevel(left: Level, right: Level): Level =
        if (severity(left) >= severity(right)) left else right

    private fun severity(level: Level): Int = when (level) {
        Level.UNKNOWN -> 0
        Level.LOW -> 1
        Level.MODERATE -> 2
        Level.HIGH -> 3
    }
}
