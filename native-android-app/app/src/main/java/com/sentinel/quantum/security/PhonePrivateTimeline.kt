package com.sentinel.quantum.security

/**
 * Local-only bounded correlation model for events already observed by Phone Core.
 * Raw message bodies are intentionally excluded.
 */
object PhonePrivateTimeline {
    enum class Kind { CALL, SMS, MMS, WIFI }

    data class Event(
        val kind: Kind,
        val timestampMs: Long,
        val direction: String,
        val signal: String? = null,
        val provenance: PhoneCoreCertificationProvenance.Scope? = null
    )

    data class Summary(
        val events: List<Event>,
        val coordinatedCallSms: Boolean
    )

    /** Reject malformed tokens instead of repairing them into certification signals. */
    fun sanitize(event: Event, nowMs: Long): Event? {
        if (event.timestampMs !in 0..nowMs) return null
        fun token(value: String, maxLength: Int): String? = value.takeIf {
            it.isNotBlank() && it.length <= maxLength &&
                it.all { character -> character.isLetterOrDigit() ||
                    character == '_' || character == '-' || character == ':' }
        }
        val direction = token(event.direction, 24) ?: return null
        val signal = event.signal?.let { token(it, 160) ?: return null }
        return event.copy(direction = direction, signal = signal)
    }

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

