package com.sentinel.quantum.security

/**
 * Shared Phone Core trust vocabulary. UNKNOWN is deliberately distinct from risk.
 */
enum class SentinelConfidence { VERIFIED, CORROBORATED, INDICATIVE, UNKNOWN }

data class PhoneEvidence(
    val code: String,
    val confidence: SentinelConfidence,
    val localOnly: Boolean = true
)

data class PhoneTrustAssessment(
    val confidence: SentinelConfidence,
    val reasons: List<String>,
    val shouldConfirmBeforeCallback: Boolean
)

object PhoneTrustEngine {
    fun assess(evidence: List<PhoneEvidence>): PhoneTrustAssessment {
        val bounded = evidence.take(MAX_EVIDENCE)
        val reasons = bounded.map { it.code.take(MAX_REASON_CHARS) }.distinct()
        val confidence = when {
            bounded.any { it.confidence == SentinelConfidence.VERIFIED } -> SentinelConfidence.VERIFIED
            bounded.count { it.confidence == SentinelConfidence.CORROBORATED } >= 2 -> SentinelConfidence.CORROBORATED
            bounded.any { it.confidence == SentinelConfidence.INDICATIVE || it.confidence == SentinelConfidence.CORROBORATED } -> SentinelConfidence.INDICATIVE
            else -> SentinelConfidence.UNKNOWN
        }
        val callbackSignals = bounded.count {
            it.code in setOf("SHORT_RING", "UNUSUAL_COUNTRY", "REPEATED_UNKNOWN", "SIGNED_REPUTATION_WARNING")
        }
        return PhoneTrustAssessment(confidence, reasons, callbackSignals >= 2)
    }

    private const val MAX_EVIDENCE = 16
    private const val MAX_REASON_CHARS = 64
}
