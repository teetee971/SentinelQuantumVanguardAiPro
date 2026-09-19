package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonePrivateTimelineTest {
    @Test fun correlatesNearbyCallAndSmsWithoutMessageContent() {
        val now = 1_000_000L
        val summary = PhonePrivateTimeline.summarize(listOf(
            PhonePrivateTimeline.Event(PhonePrivateTimeline.Kind.CALL, now - 1000, "INCOMING", "SHORT_RING"),
            PhonePrivateTimeline.Event(PhonePrivateTimeline.Kind.SMS, now - 2000, "INCOMING", "SUSPICIOUS_LINK")
        ), now)
        assertTrue(summary.coordinatedCallSms)
    }

    @Test fun ignoresFutureEvents() {
        val now = 1_000_000L
        val summary = PhonePrivateTimeline.summarize(listOf(
            PhonePrivateTimeline.Event(PhonePrivateTimeline.Kind.CALL, now + 1, "INCOMING")
        ), now)
        assertTrue(summary.events.isEmpty())
        assertFalse(summary.coordinatedCallSms)
    }
}
