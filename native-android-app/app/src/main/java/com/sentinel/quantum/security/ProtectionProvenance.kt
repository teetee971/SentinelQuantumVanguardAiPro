package com.sentinel.quantum.security

/**
 * Explicit provenance boundary for identity and anti-fraud facts shown by Sentinel.
 * Prevents local facts, official directory data, community reports and heuristic analysis
 * from being presented as if they had the same evidentiary weight.
 */
object ProtectionProvenance {
    enum class Source {
        LOCAL_CONTACT,
        OFFICIAL_DIRECTORY,
        COMMUNITY_REPORT,
        SENTINEL_ANALYSIS
    }

    enum class Confidence {
        VERIFIED,
        CORROBORATED,
        INDICATIVE,
        UNKNOWN
    }

    data class Evidence(
        val source: Source,
        val label: String,
        val confidence: Confidence,
        val observedAtEpochMillis: Long? = null
    ) {
        init {
            require(label.isNotBlank()) { "label must not be blank" }
            require(label.length <= MAX_LABEL_CHARS) { "label too long" }
            require(observedAtEpochMillis == null || observedAtEpochMillis >= 0) {
                "invalid observation timestamp"
            }
        }
    }

    fun canAssertVerifiedFact(evidence: Evidence): Boolean =
        evidence.source == Source.OFFICIAL_DIRECTORY &&
            evidence.confidence == Confidence.VERIFIED

    fun displayPrefix(source: Source): String = when (source) {
        Source.LOCAL_CONTACT -> "CONTACT LOCAL"
        Source.OFFICIAL_DIRECTORY -> "SOURCE OFFICIELLE"
        Source.COMMUNITY_REPORT -> "COMMUNAUTÉ"
        Source.SENTINEL_ANALYSIS -> "ANALYSE SENTINEL"
    }

    private const val MAX_LABEL_CHARS = 160
}
