package com.sentinel.quantum.security

/**
 * Local-only adapter that combines the existing SMS analyzer result with fresh cached
 * communication reputation for the same sender.
 *
 * This class performs no network access and never persists SMS content or the raw sender number.
 */
class IncomingSmsRiskContext(
    private val cache: CommunicationReputationCache
) {
    fun assess(
        sender: String,
        sms: SmsLinkAnalyzer.Analysis?,
        nowMs: Long = System.currentTimeMillis()
    ): UnifiedCommunicationRisk.Assessment {
        val reputation = cache.get(sender, nowMs)?.let { entry ->
            PhoneRiskCard.build(
                PhoneRiskCard.Input(
                    riskScore = entry.riskScore,
                    communitySignals = entry.signals,
                    categoryCodes = entry.categories,
                    sourceLabel = SOURCE_LABEL,
                    observedAtMs = entry.observedAtMs,
                    ttlMs = entry.ttlMs
                ),
                nowMs = nowMs
            )
        }
        return UnifiedCommunicationRisk.assess(sms = sms, reputation = reputation)
    }

    private companion object {
        const val SOURCE_LABEL = "Réputation Sentinel locale"
    }
}
