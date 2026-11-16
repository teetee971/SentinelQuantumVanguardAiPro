package com.sentinel.quantum.security

/** UI-safe projection of persisted call-filter history. Internal number fingerprints never cross this boundary. */
data class CallHistoryPresentation(
    val id: Long,
    val occurredAtMs: Long,
    val action: String,
    val reason: String,
    val source: String,
    val hasPrivateIdentifier: Boolean
)

object CallHistoryPresentationMapper {
    fun from(entry: CallFilterDecisionEntity): CallHistoryPresentation =
        CallHistoryPresentation(
            id = entry.id,
            occurredAtMs = entry.occurredAtMs,
            action = PhoneCoreFrenchLabels.action(sanitize(entry.action, 24)),
            reason = PhoneCoreFrenchLabels.reason(sanitize(entry.reason, 64)),
            source = PhoneCoreFrenchLabels.source(sanitize(entry.source, 32)),
            hasPrivateIdentifier = entry.numberFingerprint != null
        )

    private fun sanitize(value: String, max: Int): String =
        value.filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == ':' }
            .take(max)
            .ifBlank { "UNKNOWN" }
}
