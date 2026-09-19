package com.sentinel.quantum.security

/**
 * Privacy-bounded presentation model for a unified Sentinel number card.
 * It deliberately carries signals and provenance, never raw SMS/MMS bodies or OTP values.
 */
object SentinelNumberCard {
    data class Identity(
        val displayName: String?,
        val organisation: String?,
        val country: String?,
        val operatorLabel: String?,
        val verified: Boolean
    )

    data class Reputation(
        val riskScore: Int?,
        val communitySignals: Int,
        val flags: List<String>
    )

    data class Card(
        val identity: Identity,
        val confidence: SentinelConfidence,
        val reasons: List<String>,
        val timeline: PhonePrivateTimeline.Summary,
        val reputation: Reputation?,
        val shouldConfirmBeforeCallback: Boolean
    )

    fun build(
        identity: Identity,
        evidence: List<PhoneEvidence>,
        events: List<PhonePrivateTimeline.Event>,
        reputation: Reputation?,
        nowMs: Long
    ): Card {
        val trust = PhoneTrustEngine.assess(evidence)
        val timeline = PhonePrivateTimeline.summarize(events, nowMs)
        return Card(
            identity = identity.sanitized(),
            confidence = trust.confidence,
            reasons = trust.reasons,
            timeline = timeline,
            reputation = reputation?.sanitized(),
            shouldConfirmBeforeCallback = trust.shouldConfirmBeforeCallback
        )
    }

    private fun Identity.sanitized() = copy(
        displayName = displayName?.take(MAX_TEXT),
        organisation = organisation?.take(MAX_TEXT),
        country = country?.take(MAX_TEXT),
        operatorLabel = operatorLabel?.take(MAX_TEXT)
    )

    private fun Reputation.sanitized() = copy(
        riskScore = riskScore?.coerceIn(0, 100),
        communitySignals = communitySignals.coerceIn(0, MAX_COMMUNITY_SIGNALS),
        flags = flags.map { it.take(MAX_FLAG) }.distinct().take(MAX_FLAGS)
    )

    private const val MAX_TEXT = 96
    private const val MAX_FLAG = 64
    private const val MAX_FLAGS = 8
    private const val MAX_COMMUNITY_SIGNALS = 1_000_000
}
