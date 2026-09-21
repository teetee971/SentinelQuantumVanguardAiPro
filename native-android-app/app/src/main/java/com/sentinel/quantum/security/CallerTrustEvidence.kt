package com.sentinel.quantum.security

/**
 * Explainable trust model for Caller ID. Risk and confidence are deliberately independent:
 * absence of evidence must never be rendered as evidence that a caller is safe.
 *
 * Pure and network-free: safe to reuse without extending the CallScreeningService critical path.
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
        val observedAtEpochMs: Long? = null,
        val expiresAtEpochMs: Long? = null
    )

    data class Assessment(
        val riskScore: Int,
        val confidenceScore: Int,
        val evidence: List<Evidence>,
        val explanation: String
    )

    fun assess(evidence: List<Evidence>, nowEpochMs: Long = System.currentTimeMillis()): Assessment {
        val bounded = evidence.asSequence()
            .filter { it.expiresAtEpochMs == null || it.expiresAtEpochMs > nowEpochMs }
            .sortedByDescending { it.observedAtEpochMs ?: Long.MIN_VALUE }
            .take(MAX_EVIDENCE)
            .toList()

        fun freshness(item: Evidence): Int {
            val observed = item.observedAtEpochMs ?: return 100
            val age = (nowEpochMs - observed).coerceAtLeast(0L)
            return when {
                age <= DAY_MS -> 100
                age <= WEEK_MS -> 80
                age <= MONTH_MS -> 55
                else -> 30
            }
        }

        val risk = bounded.filter { it.kind == Kind.RISK }
            .sumOf { it.weight.coerceIn(0, 100) * freshness(it) / 100 }
            .coerceIn(0, 100)
        val trust = bounded.filter { it.kind == Kind.TRUST }
            .sumOf { it.weight.coerceIn(0, 100) * freshness(it) / 100 }
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

    const val MAX_EVIDENCE = 24
    const val DAY_MS = 86_400_000L
    const val WEEK_MS = 7 * DAY_MS
    const val MONTH_MS = 30 * DAY_MS
}
