package com.sentinel.quantum.security

/**
 * Explainable, local-only risk dossier for a device-bound network subject.
 *
 * Risk and confidence are deliberately independent. A low risk score with little
 * evidence must never be rendered as proof that a device is safe.
 *
 * This component consumes already-produced evidence only. It performs no scanning,
 * packet capture, payload inspection, identity attribution or remote lookup.
 */
enum class NetworkRiskEvidenceType {
    PRESENCE_CHANGE,
    IDENTITY_CHANGE,
    TOPOLOGY_CHANGE,
    COVERT_DEVICE_HINT,
    FLOW_ANOMALY,
    BEHAVIOR_ANOMALY,
    SERVICE_EXPOSURE
}

enum class NetworkRiskEvidenceSource {
    ANDROID_WIFI,
    ANDROID_BLE,
    LOCAL_AGENT,
    FLOW_ANALYZER,
    BEHAVIOR_BASELINE,
    TOPOLOGY_ENGINE,
    USER_CONFIRMED
}

enum class NetworkRiskConfidence {
    UNKNOWN,
    INDICATIVE,
    CORROBORATED,
    STRONG_CORROBORATION
}

data class NetworkRiskEvidence(
    val type: NetworkRiskEvidenceType,
    val source: NetworkRiskEvidenceSource,
    val confidencePercent: Int,
    val observedAtMs: Long,
    val summary: String
)

data class NetworkRiskReason(
    val type: NetworkRiskEvidenceType,
    val source: NetworkRiskEvidenceSource,
    val contribution: Int,
    val summary: String
)

data class NetworkRiskDossier(
    val subjectFingerprint: String,
    val riskScore: Int,
    val confidence: NetworkRiskConfidence,
    val evidenceCount: Int,
    val independentSourceCount: Int,
    val corroborated: Boolean,
    val firstObservedAtMs: Long?,
    val lastObservedAtMs: Long?,
    val topReasons: List<NetworkRiskReason>,
    val explanation: String
)

object NetworkRiskDossierBuilder {
    private const val FINGERPRINT_HEX_LENGTH = 64
    private const val MAX_EVIDENCE = 5_000
    private const val MAX_SUMMARY_LENGTH = 160
    private const val MAX_REASONS = 5

    /**
     * Returns null when [subjectFingerprint] is not a device-bound SHA-256/HMAC-style
     * hexadecimal fingerprint. Raw MAC/BSSID identifiers are intentionally rejected.
     */
    fun build(
        subjectFingerprint: String,
        evidence: List<NetworkRiskEvidence>
    ): NetworkRiskDossier? {
        val fingerprint = normalizeFingerprint(subjectFingerprint) ?: return null

        val accepted = evidence
            .take(MAX_EVIDENCE)
            .mapNotNull(::normalizeEvidence)
            .distinctBy {
                listOf(
                    it.type.name,
                    it.source.name,
                    it.observedAtMs.toString(),
                    it.confidencePercent.toString(),
                    it.summary.lowercase()
                ).joinToString("|")
            }

        val sources = accepted.map { it.source }.toSet()
        val confidence = confidenceFor(sources.size)
        val corroborated = sources.size >= 2

        val reasons = accepted
            .map { record ->
                NetworkRiskReason(
                    type = record.type,
                    source = record.source,
                    contribution = contribution(record),
                    summary = record.summary
                )
            }
            .filter { it.contribution > 0 }
            .sortedWith(
                compareByDescending<NetworkRiskReason> { it.contribution }
                    .thenBy { it.type.name }
                    .thenBy { it.source.name }
            )
            .take(MAX_REASONS)

        val rawRisk = accepted.sumOf(::contribution)
        val corroborationBonus = when {
            sources.size >= 3 -> 20
            sources.size == 2 -> 10
            else -> 0
        }
        val riskScore = (rawRisk + corroborationBonus).coerceIn(0, 100)

        return NetworkRiskDossier(
            subjectFingerprint = fingerprint,
            riskScore = riskScore,
            confidence = confidence,
            evidenceCount = accepted.size,
            independentSourceCount = sources.size,
            corroborated = corroborated,
            firstObservedAtMs = accepted.minOfOrNull { it.observedAtMs },
            lastObservedAtMs = accepted.maxOfOrNull { it.observedAtMs },
            topReasons = reasons,
            explanation = explanationFor(
                riskScore = riskScore,
                confidence = confidence,
                evidenceCount = accepted.size
            )
        )
    }

    private fun normalizeEvidence(record: NetworkRiskEvidence): NetworkRiskEvidence? {
        if (record.confidencePercent !in 0..100) return null
        if (record.observedAtMs < 0L) return null

        val summary = record.summary
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_SUMMARY_LENGTH)
        if (summary.isEmpty()) return null

        return record.copy(summary = summary)
    }

    private fun contribution(record: NetworkRiskEvidence): Int {
        val base = when (record.type) {
            NetworkRiskEvidenceType.PRESENCE_CHANGE -> 5
            NetworkRiskEvidenceType.IDENTITY_CHANGE -> 15
            NetworkRiskEvidenceType.TOPOLOGY_CHANGE -> 15
            NetworkRiskEvidenceType.COVERT_DEVICE_HINT -> 30
            NetworkRiskEvidenceType.FLOW_ANOMALY -> 30
            NetworkRiskEvidenceType.BEHAVIOR_ANOMALY -> 25
            NetworkRiskEvidenceType.SERVICE_EXPOSURE -> 20
        }
        return (base * record.confidencePercent) / 100
    }

    private fun confidenceFor(sourceCount: Int): NetworkRiskConfidence = when {
        sourceCount >= 3 -> NetworkRiskConfidence.STRONG_CORROBORATION
        sourceCount == 2 -> NetworkRiskConfidence.CORROBORATED
        sourceCount == 1 -> NetworkRiskConfidence.INDICATIVE
        else -> NetworkRiskConfidence.UNKNOWN
    }

    private fun explanationFor(
        riskScore: Int,
        confidence: NetworkRiskConfidence,
        evidenceCount: Int
    ): String {
        if (evidenceCount == 0) {
            return "Aucune preuve exploitable : l’absence de signal ne prouve pas que l’appareil est sûr."
        }

        val riskText = when {
            riskScore >= 70 -> "Risque local élevé"
            riskScore >= 35 -> "Risque local à vérifier"
            else -> "Risque local actuellement limité"
        }

        val confidenceText = when (confidence) {
            NetworkRiskConfidence.STRONG_CORROBORATION -> "preuves issues d’au moins trois sources indépendantes"
            NetworkRiskConfidence.CORROBORATED -> "preuves corroborées par deux sources indépendantes"
            NetworkRiskConfidence.INDICATIVE -> "une seule source indépendante ; résultat indicatif"
            NetworkRiskConfidence.UNKNOWN -> "confiance non déterminée"
        }

        return "$riskText ; $confidenceText."
    }

    private fun normalizeFingerprint(value: String): String? {
        val normalized = value.trim().lowercase()
        if (normalized.length != FINGERPRINT_HEX_LENGTH) return null
        if (!normalized.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return normalized
    }
}
