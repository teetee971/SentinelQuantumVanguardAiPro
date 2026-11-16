package com.sentinel.quantum.security

/**
 * Maps consented remote caller-reputation results into the bounded local cache contract.
 *
 * Fields that are only meaningful to the remote UI response (action, flags, country, warning,
 * community copy) are intentionally excluded from local cross-module persistence.
 */
object CommunicationReputationBridge {
    fun entryFrom(result: CallerReputationClient.Result): CommunicationReputationCache.Entry? {
        val riskScore = result.riskScore ?: return null
        val observedAtMs = result.reputationObservedAtMs ?: return null
        val ttlMs = result.reputationTtlMs ?: return null

        if (riskScore !in 0..100) return null
        if (result.signals < 0) return null
        if (observedAtMs <= 0L) return null
        if (ttlMs <= 0L) return null

        return CommunicationReputationCache.Entry(
            riskScore = riskScore,
            categories = result.categories,
            signals = result.signals,
            observedAtMs = observedAtMs,
            ttlMs = ttlMs
        )
    }
}
