package com.sentinel.quantum.security

/**
 * Local-only bounded correlation model for events already observed by Phone Core.
 * Raw message bodies are intentionally excluded.
 */
object PhonePrivateTimeline {
    enum class Kind { CALL, SMS, MMS }

    data class Event(
        val kind: Kind,
        val timestampMs: Long,
        val direction: String,
        val signal: String? = null
    )

    data class Summary(
        val events: List<Event>,
        val coordinatedCallSms: Boolean
    )

    fun summarize(events: List<Event>, nowMs: Long): Summary {
        val bounded = events.asSequence()
            .filter { it.timestampMs in 0..nowMs }
            .filter { nowMs - it.timestampMs <= RETENTION_MS }
            .sortedByDescending { it.timestampMs }
            .take(MAX_EVENTS)
            .toList()

        val calls = bounded.filter { it.kind == Kind.CALL && it.direction == "INCOMING" }
        val sms = bounded.filter { it.kind == Kind.SMS && it.direction == "INCOMING" }
        val coordinated = calls.any { call ->
            sms.any { message -> kotlin.math.abs(call.timestampMs - message.timestampMs) <= CORRELATION_WINDOW_MS }
        }
        return Summary(bounded, coordinated)
    }

    private const val MAX_EVENTS = 100
    private const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
    private const val CORRELATION_WINDOW_MS = 10L * 60L * 1000L
}
