package com.sentinel.quantum.security

/**
 * Deterministic local correlation of already-observed network evidence.
 *
 * This engine does not scan, capture packets, decrypt traffic, identify people or
 * contact remote services. It correlates bounded evidence records that other reviewed
 * components have already produced.
 */
enum class NetworkEvidenceKind {
    PRESENCE,
    IDENTITY_CHANGE,
    RISK_SIGNAL,
    COVERT_DEVICE_HINT,
    FLOW_ANOMALY,
    TOPOLOGY_CHANGE
}

enum class NetworkEvidenceSource {
    ANDROID_WIFI,
    ANDROID_BLE,
    LOCAL_AGENT,
    FLOW_ANALYZER,
    TOPOLOGY_ENGINE,
    USER_CONFIRMED
}

data class NetworkEvidenceRecord(
    val subjectFingerprint: String,
    val kind: NetworkEvidenceKind,
    val source: NetworkEvidenceSource,
    val confidencePercent: Int,
    val observedAtMs: Long
)

data class CorrelatedNetworkSubject(
    val subjectFingerprint: String,
    val evidenceCount: Int,
    val independentSourceCount: Int,
    val riskScore: Int,
    val corroborated: Boolean,
    val firstObservedAtMs: Long,
    val lastObservedAtMs: Long,
    val kinds: Set<NetworkEvidenceKind>,
    val sources: Set<NetworkEvidenceSource>
)

data class NetworkCorrelationSnapshot(
    val acceptedEvidence: Int,
    val rejectedEvidence: Int,
    val subjects: List<CorrelatedNetworkSubject>
)

object NetworkEvidenceCorrelationEngine {
    private const val MAX_EVIDENCE = 10_000
    private const val FINGERPRINT_HEX_LENGTH = 64
    private const val CORROBORATED_SOURCE_COUNT = 2

    fun correlate(records: List<NetworkEvidenceRecord>): NetworkCorrelationSnapshot {
        val bounded = records.take(MAX_EVIDENCE)
        val rejectedForLimit = records.size - bounded.size
        val accepted = bounded
            .mapNotNull(::normalize)
            .distinctBy {
                listOf(
                    it.subjectFingerprint,
                    it.kind.name,
                    it.source.name,
                    it.observedAtMs.toString(),
                    it.confidencePercent.toString()
                ).joinToString("|")
            }

        val rejectedInvalidOrDuplicate = bounded.size - accepted.size

        val subjects = accepted
            .groupBy { it.subjectFingerprint }
            .map { (fingerprint, evidence) ->
                val sources = evidence.map { it.source }.toSet()
                val kinds = evidence.map { it.kind }.toSet()
                CorrelatedNetworkSubject(
                    subjectFingerprint = fingerprint,
                    evidenceCount = evidence.size,
                    independentSourceCount = sources.size,
                    riskScore = calculateRiskScore(evidence),
                    corroborated = sources.size >= CORROBORATED_SOURCE_COUNT,
                    firstObservedAtMs = evidence.minOf { it.observedAtMs },
                    lastObservedAtMs = evidence.maxOf { it.observedAtMs },
                    kinds = kinds,
                    sources = sources
                )
            }
            .sortedWith(
                compareByDescending<CorrelatedNetworkSubject> { it.riskScore }
                    .thenByDescending { it.lastObservedAtMs }
            )

        return NetworkCorrelationSnapshot(
            acceptedEvidence = accepted.size,
            rejectedEvidence = rejectedForLimit + rejectedInvalidOrDuplicate,
            subjects = subjects
        )
    }

    private fun normalize(record: NetworkEvidenceRecord): NetworkEvidenceRecord? {
        val fingerprint = record.subjectFingerprint.trim().lowercase()
        if (fingerprint.length != FINGERPRINT_HEX_LENGTH) return null
        if (!fingerprint.all { it in '0'..'9' || it in 'a'..'f' }) return null
        if (record.confidencePercent !in 0..100) return null
        if (record.observedAtMs < 0L) return null
        return record.copy(subjectFingerprint = fingerprint)
    }

    private fun calculateRiskScore(evidence: List<NetworkEvidenceRecord>): Int {
        if (evidence.isEmpty()) return 0

        val weighted = evidence.sumOf { record ->
            val base = when (record.kind) {
                NetworkEvidenceKind.PRESENCE -> 5
                NetworkEvidenceKind.IDENTITY_CHANGE -> 15
                NetworkEvidenceKind.RISK_SIGNAL -> 25
                NetworkEvidenceKind.COVERT_DEVICE_HINT -> 30
                NetworkEvidenceKind.FLOW_ANOMALY -> 30
                NetworkEvidenceKind.TOPOLOGY_CHANGE -> 20
            }
            (base * record.confidencePercent) / 100
        }

        val independentSources = evidence.map { it.source }.toSet().size
        val corroborationBonus = when {
            independentSources >= 3 -> 20
            independentSources == 2 -> 10
            else -> 0
        }

        return (weighted + corroborationBonus).coerceIn(0, 100)
    }
}
