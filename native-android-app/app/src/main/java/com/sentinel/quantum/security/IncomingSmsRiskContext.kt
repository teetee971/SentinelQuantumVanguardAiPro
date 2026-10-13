package com.sentinel.quantum.security

/**
 * Local-only adapter that combines the existing SMS analyzer result with fresh cached
 * communication reputation for the same sender.
 *
 * This class performs no network access and never persists SMS content or the raw sender number.
 * Cached remote-derived reputation is ignored when the current privacy/consent policy no longer
 * permits its use.
 */
class IncomingSmsRiskContext(
    private val cache: CommunicationReputationCache
) {
    fun assess(
        sender: String,
        sms: SmsLinkAnalyzer.Analysis?,
        allowCachedReputation: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ): UnifiedCommunicationRisk.Assessment {
        val reputation = if (allowCachedReputation) {
            cache.get(sender, nowMs)?.let { entry ->
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
        } else {
            null
        }
        return UnifiedCommunicationRisk.assess(sms = sms, reputation = reputation)
    }

    private companion object {
        const val SOURCE_LABEL = "Réputation Sentinel locale"
    }
}
