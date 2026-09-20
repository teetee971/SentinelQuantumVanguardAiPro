package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class SentinelNumberCardTest {
    @Test fun combinesTrustTimelineAndBoundedReputation() {
        val now = 2_000_000L
        val card = SentinelNumberCard.build(
            identity = SentinelNumberCard.Identity("Alice", "Example", "FR", "Operator", false),
            evidence = listOf(
                PhoneEvidence("SHORT_RING", SentinelConfidence.INDICATIVE),
                PhoneEvidence("UNUSUAL_COUNTRY", SentinelConfidence.INDICATIVE)
            ),
            events = listOf(
                PhonePrivateTimeline.Event(PhonePrivateTimeline.Kind.CALL, now - 60_000, "INCOMING"),
                PhonePrivateTimeline.Event(PhonePrivateTimeline.Kind.SMS, now - 30_000, "INCOMING", "LINK")
            ),
            reputation = SentinelNumberCard.Reputation(140, -4, listOf("WANGIRI", "WANGIRI")),
            nowMs = now
        )
        assertEquals(SentinelConfidence.INDICATIVE, card.confidence)
        assertTrue(card.shouldConfirmBeforeCallback)
        assertTrue(card.timeline.coordinatedCallSms)
        assertEquals(100, card.reputation?.riskScore)
        assertEquals(0, card.reputation?.communitySignals)
        assertEquals(listOf("WANGIRI"), card.reputation?.flags)
    }

    @Test fun outgoingEventsDoNotCreateInboundCorrelation() {
        val now = 2_000_000L
        val summary = PhonePrivateTimeline.summarize(
            events = listOf(
                PhonePrivateTimeline.Event(PhonePrivateTimeline.Kind.CALL, now - 60_000, "OUTGOING"),
                PhonePrivateTimeline.Event(PhonePrivateTimeline.Kind.SMS, now - 30_000, "INCOMING", "LINK")
            ),
            nowMs = now
        )
        assertFalse(summary.coordinatedCallSms)
    }

    @Test fun unknownEvidenceIsNotConvertedIntoRisk() {
        val card = SentinelNumberCard.build(
            identity = SentinelNumberCard.Identity(null, null, null, null, false),
            evidence = emptyList(),
            events = emptyList(),
            reputation = null,
            nowMs = 1L
        )
        assertEquals(SentinelConfidence.UNKNOWN, card.confidence)
        assertFalse(card.shouldConfirmBeforeCallback)
        assertTrue(card.reasons.isEmpty())
    }
}
