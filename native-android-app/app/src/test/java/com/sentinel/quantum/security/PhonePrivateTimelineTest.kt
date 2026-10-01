package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    @Test fun malformedProofIsRejectedInsteadOfConvertedToSuccess() {
        val event = PhonePrivateTimeline.Event(PhonePrivateTimeline.Kind.SMS, 100L, "OUTGOING", "SMS_ALL_PARTS_SENT!")
        assertNull(PhonePrivateTimeline.sanitize(event, 100L))
        assertNull(PhonePrivateTimeline.sanitize(event.copy(signal = "SMS_ ALL_PARTS_SENT"), 100L))
        assertNull(PhonePrivateTimeline.sanitize(event.copy(direction = "OUT GOING"), 100L))
    }

    @Test fun validProofRemainsExactAndOversizedOrFutureEventsAreRejected() {
        val event = PhonePrivateTimeline.Event(PhonePrivateTimeline.Kind.SMS, 100L, "OUTGOING", "SMS_ALL_PARTS_SENT")
        assertEquals(event, PhonePrivateTimeline.sanitize(event, 100L))
        assertNull(PhonePrivateTimeline.sanitize(event.copy(signal = "A".repeat(161)), 100L))
        assertNull(PhonePrivateTimeline.sanitize(event.copy(timestampMs = 101L), 100L))
    }
}
