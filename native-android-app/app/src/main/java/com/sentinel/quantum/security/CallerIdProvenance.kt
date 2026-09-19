package com.sentinel.quantum.security

/**
 * Maps caller-ID inputs to explicit evidence labels without upgrading heuristic or
 * community signals into verified facts.
 */
object CallerIdProvenance {
    fun localIdentity(displayName: String?, organisation: String?): ProtectionProvenance.Evidence? {
        val label = listOfNotNull(
            displayName?.trim()?.takeIf { it.isNotBlank() },
            organisation?.trim()?.takeIf { it.isNotBlank() }
        ).joinToString(" · ").take(160)
        if (label.isBlank()) return null
        return ProtectionProvenance.Evidence(
            source = ProtectionProvenance.Source.LOCAL_CONTACT,
            label = label,
            confidence = ProtectionProvenance.Confidence.VERIFIED
        )
    }

    fun sentinelDecision(reason: String): ProtectionProvenance.Evidence =
        ProtectionProvenance.Evidence(
            source = ProtectionProvenance.Source.SENTINEL_ANALYSIS,
            label = reason.trim().ifBlank { "Décision locale sans motif détaillé" }.take(160),
            confidence = ProtectionProvenance.Confidence.INDICATIVE
        )

    fun communitySignal(label: String): ProtectionProvenance.Evidence =
        ProtectionProvenance.Evidence(
            source = ProtectionProvenance.Source.COMMUNITY_REPORT,
            label = label.trim().ifBlank { "Signalement communautaire" }.take(160),
            confidence = ProtectionProvenance.Confidence.INDICATIVE
        )
}
