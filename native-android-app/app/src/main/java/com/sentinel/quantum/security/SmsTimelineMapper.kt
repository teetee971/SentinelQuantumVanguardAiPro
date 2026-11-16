package com.sentinel.quantum.security

/** Privacy-bounded mapping: no raw SMS body, URL, sender identifier or OTP value is represented. */
object SmsTimelineMapper {
    fun toEvent(analysis: SmsLinkAnalyzer.Analysis, direction: String = "INCOMING"): PhonePrivateTimeline.Event? {
        if (!analysis.accepted) return null
        return PhonePrivateTimeline.Event(
            kind = PhonePrivateTimeline.Kind.SMS,
            timestampMs = analysis.analyzedAt,
            direction = sanitize(direction, 16),
            signal = buildSignal(analysis)
        )
    }

    private fun buildSignal(analysis: SmsLinkAnalyzer.Analysis): String {
        val codes = analysis.findings.asSequence()
            .map { sanitize(it.code, 32) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
            .toList()
        return (listOf("RISK_" + analysis.riskLevel.name) + codes).joinToString(":").take(160)
    }

    private fun sanitize(value: String, max: Int): String =
        value.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(max).ifBlank { "UNKNOWN" }
}
