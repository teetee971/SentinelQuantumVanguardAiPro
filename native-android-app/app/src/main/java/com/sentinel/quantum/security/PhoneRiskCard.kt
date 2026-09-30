package com.sentinel.quantum.security

/**
 * Truthful presentation model for Sentinel's caller/message risk card.
 *
 * This class never makes a blocking decision. It converts an already-produced reputation score
 * and bounded signals into UI-safe labels while preserving UNKNOWN and STALE states.
 */
object PhoneRiskCard {
    enum class RiskBand {
        UNKNOWN,
        LOW,
        MODERATE,
        HIGH,
        CRITICAL
    }

    enum class Freshness {
        UNKNOWN,
        FRESH,
        STALE
    }

    data class Input(
        val riskScore: Int?,
        val communitySignals: Int = 0,
        val flags: List<String> = emptyList(),
        val sourceLabel: String? = null,
        val observedAtMs: Long? = null,
        val ttlMs: Long? = null
    )

    data class Card(
        val riskBand: RiskBand,
        val riskScore: Int?,
        val communitySignals: Int,
        val categories: List<PhoneFraudCategory>,
        val sourceLabel: String?,
        val freshness: Freshness,
        val reasons: List<String>
    )

    fun build(input: Input, nowMs: Long): Card {
        val validatedScore = input.riskScore?.takeIf { it in 0..100 }
        val freshness = freshness(input.observedAtMs, input.ttlMs, nowMs)
        val currentScore = validatedScore.takeUnless { freshness == Freshness.STALE }
        val categories = input.flags
            .mapNotNull(PhoneFraudTaxonomy::fromSignal)
            .distinct()
            .take(MAX_CATEGORIES)
        val signals = input.communitySignals.coerceIn(0, MAX_COMMUNITY_SIGNALS)
        val source = input.sourceLabel
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.take(MAX_SOURCE_CHARS)

        val reasons = buildList {
            categories.forEach { add(it.frenchLabel) }
            if (signals > 0) {
                add(
                    signals.toString() +
                        " signalement" + (if (signals > 1) "s" else "") +
                        " communautaire" + (if (signals > 1) "s" else "")
                )
            }
            if (freshness == Freshness.STALE) add("Données de réputation expirées")
            if (freshness == Freshness.UNKNOWN && currentScore != null) {
                add("Fraîcheur des données non déterminée")
            }
        }.distinct().take(MAX_REASONS)

        return Card(
            riskBand = bandFor(currentScore),
            riskScore = currentScore,
            communitySignals = signals,
            categories = categories,
            sourceLabel = source,
            freshness = freshness,
            reasons = reasons
        )
    }

    private fun freshness(observedAtMs: Long?, ttlMs: Long?, nowMs: Long): Freshness {
        if (observedAtMs == null || ttlMs == null || ttlMs <= 0L || nowMs < 0L) return Freshness.UNKNOWN
        if (observedAtMs < 0L || observedAtMs > nowMs) return Freshness.UNKNOWN
        return if (nowMs - observedAtMs <= ttlMs) Freshness.FRESH else Freshness.STALE
    }

    private fun bandFor(score: Int?): RiskBand = when {
        score == null -> RiskBand.UNKNOWN
        score >= 85 -> RiskBand.CRITICAL
        score >= 70 -> RiskBand.HIGH
        score >= 40 -> RiskBand.MODERATE
        else -> RiskBand.LOW
    }

    private const val MAX_CATEGORIES = 6
    private const val MAX_REASONS = 8
    private const val MAX_SOURCE_CHARS = 96
    private const val MAX_COMMUNITY_SIGNALS = 1_000_000
}
