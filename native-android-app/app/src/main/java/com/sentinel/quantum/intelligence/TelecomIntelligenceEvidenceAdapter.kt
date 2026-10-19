package com.sentinel.quantum.intelligence

import com.sentinel.quantum.security.InternationalNumberResolver

/** Maps local numbering evidence to a privacy-bounded, non-enforcing Intelligence event. */
object TelecomIntelligenceEvidenceAdapter {
    fun from(
        resolution: InternationalNumberResolver.Resolution,
        sourceIds: List<String>,
        freshness: IntelligenceEvidence.TelecomFreshness = IntelligenceEvidence.TelecomFreshness.UNKNOWN
    ): IntelligenceEvidence.TelecomNumberEnrichment {
        val sources = sourceIds
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .distinct()
            .take(8)
        return IntelligenceEvidence.TelecomNumberEnrichment(
            resolutionStatus = resolution.status.name,
            callingCode = resolution.matchingEntries.firstOrNull()?.prefix,
            jurisdictions = resolution.matchingEntries.map { it.name }.distinct().take(8),
            sourceIds = sources,
            freshness = freshness,
            authority = IntelligenceEvidence.TelecomAuthority.ANNOTATION_ONLY
        )
    }
}
