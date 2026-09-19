package com.sentinel.quantum.security

/**
 * Privacy boundary between persisted call-filter records and the shared Phone Core timeline.
 * Device-bound number fingerprints never cross this adapter.
 */
object CallHistoryTimelineAdapter {
    fun toEvents(
        records: List<CallFilterDecisionEntity>,
        nowMs: Long
    ): List<PhonePrivateTimeline.Event> =
        records.asSequence()
            .filter { it.occurredAtMs in 0..nowMs }
            .map { record ->
                PhonePrivateTimeline.Event(
                    kind = PhonePrivateTimeline.Kind.CALL,
                    timestampMs = record.occurredAtMs,
                    direction = "INCOMING",
                    signal = sanitizeSignal(record.action, record.reason, record.source)
                )
            }
            .toList()

    private fun sanitizeSignal(action: String, reason: String, source: String): String {
        fun token(value: String, max: Int) = value
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == ':' }
            .take(max)
            .ifBlank { "UNKNOWN" }

        return listOf(
            token(action, 24),
            token(reason, 64),
            token(source, 32)
        ).joinToString(":")
    }
}
