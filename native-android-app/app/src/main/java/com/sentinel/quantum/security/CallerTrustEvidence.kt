package com.sentinel.quantum.security

/**
 * Explainable trust model for Caller ID. Risk and confidence are deliberately independent:
 * absence of evidence must never be rendered as evidence that a caller is safe.
 *
 * This model is pure and network-free so it can be reused by UI/tests without extending the
 * CallScreeningService critical path.
 */
object CallerTrustEvidence {
    enum class Source { NETWORK, LOCAL_RULE, LOCAL_HISTORY, CONTACTS, COMMUNITY, OFFICIAL_REGISTRY }
    enum class Kind { RISK, TRUST, IDENTITY, CONTEXT }

    data class Evidence(
        val source: Source,
        val kind: Kind,
        val code: String,
        val label: String,
        val weight: Int,
        val observedAtEpochMs: Long? = null,\n        val expiresAtEpochMs: Long? = null
    )

    data class Assessment(
        val riskScore: Int,
        val confidenceScore: Int,
        val evidence: List<Evidence>,
        val explanation: String
    )

    fun assess(evidence: List<Evidence>): Assessment {
        val bounded = evidence.take(MAX_EVIDENCE)
        val risk = bounded.filter { it.kind == Kind.RISK }.sumOf { it.weight.coerceIn(0, 100) }
            .coerceIn(0, 100)
        val trust = bounded.filter { it.kind == Kind.TRUST }.sumOf { e -> e.weight.coerceIn(0, 100) * freshness(e) / 100 }
            .coerceIn(0, 100)
        val independentSources = bounded.map { it.source }.distinct().size
        val confidence = (independentSources * 18 + minOf(bounded.size, 5) * 4).coerceIn(0, 100)
        val adjustedRisk = (risk - trust / 2).coerceIn(0, 100)
        val explanation = when {
            bounded.isEmpty() -> "Données insuffisantes : aucun signal disponible"
            adjustedRisk >= 70 -> "Risque élevé selon les signaux disponibles"
            adjustedRisk >= 40 -> "Risque modéré selon les signaux disponibles"
            else -> "Risque détecté faible, sans garantie d’identité ou de sécurité"
        }
        return Assessment(adjustedRisk, confidence, bounded, explanation)
    }

    const val MAX_EVIDENCE = 24\n    const val DAY_MS = 86_400_000L\n    const val WEEK_MS = 7 * DAY_MS\n    const val MONTH_MS = 30 * DAY_MS
}
