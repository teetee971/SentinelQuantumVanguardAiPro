package com.sentinel.quantum.security

/**
 * Maps a call-screening decision to the privacy-bounded Phone Core timeline.
 * The normalized phone number carried by CallRuleEngine.Decision is deliberately ignored.
 */
object CallTimelineMapper {
    fun toEvent(
        decision: CallRuleEngine.Decision,
        timestampMs: Long = System.currentTimeMillis()
    ): PhonePrivateTimeline.Event = PhonePrivateTimeline.Event(
        kind = PhonePrivateTimeline.Kind.CALL,
        timestampMs = timestampMs,
        direction = "INCOMING",
        signal = listOf(decision.action.name, decision.reason, decision.source.name)
            .joinToString(":")
            .take(160)
    )
}
