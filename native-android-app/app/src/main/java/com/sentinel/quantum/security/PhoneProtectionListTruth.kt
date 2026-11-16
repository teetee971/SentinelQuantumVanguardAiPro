package com.sentinel.quantum.security

/**
 * Pure truth policy for the Phone Core protection-list UI.
 *
 * The UI must never infer "active" from a label or a configured endpoint alone. A source is
 * active only when a verified, non-empty local payload is actually applicable at [nowEpochMs].
 */
object PhoneProtectionListTruth {
    enum class ListStatus {
        ACTIVE,
        DISABLED,
        EXPIRED,
        UNAVAILABLE
    }

    data class SourceFacts(
        val packagePresent: Boolean,
        val verified: Boolean,
        val enabledByUser: Boolean,
        val itemCount: Int,
        val expiresAtMs: Long? = null
    )

    fun status(facts: SourceFacts, nowEpochMs: Long): ListStatus {
        if (nowEpochMs < 0L || !facts.packagePresent || !facts.verified || facts.itemCount <= 0) {
            return ListStatus.UNAVAILABLE
        }
        if (facts.expiresAtMs?.let { it <= nowEpochMs } == true) return ListStatus.EXPIRED
        if (!facts.enabledByUser) return ListStatus.DISABLED
        return ListStatus.ACTIVE
    }

    enum class CommunityVerdict {
        LEGITIMATE,
        SPAM_PROBABLE,
        INSUFFICIENT,
        NOT_EVALUATED
    }

    /**
     * The server remains responsible for moderation/classification. The client only translates a
     * bounded, already-moderated label and refuses to present a single observation as consensus.
     */
    fun communityVerdict(signals: Int, communityIntelligence: String): CommunityVerdict {
        if (signals <= 0) return CommunityVerdict.NOT_EVALUATED
        if (signals < MIN_SIGNALS_FOR_CONSENSUS) return CommunityVerdict.INSUFFICIENT
        return when (communityIntelligence.trim().lowercase()) {
            "legitimate", "identified", "trusted" -> CommunityVerdict.LEGITIMATE
            "spam_probable", "spam", "suspicious" -> CommunityVerdict.SPAM_PROBABLE
            else -> CommunityVerdict.INSUFFICIENT
        }
    }

    /** Community reputation is advisory and can never become an automatic block by itself. */
    fun mayAutomaticallyBlockFromCommunity(
        signals: Int,
        communityIntelligence: String
    ): Boolean {
        communityVerdict(signals, communityIntelligence)
        return false
    }

    const val MIN_SIGNALS_FOR_CONSENSUS = 2
}
