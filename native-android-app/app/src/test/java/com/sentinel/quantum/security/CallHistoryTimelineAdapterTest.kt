package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CallHistoryTimelineAdapterTest {
    @Test
    fun excludesFingerprintFromTimelineEvent() {
        val records = listOf(
            CallFilterDecisionEntity(
                id = 7,
                occurredAtMs = 1_000L,
                action = "SILENCE",
                reason = "SIGNED_REPUTATION_PREFIX",
                source = "SIGNED_REPUTATION",
                numberFingerprint = "device-bound-secret-fingerprint"
            )
        )

        val events = CallHistoryTimelineAdapter.toEvents(records, 2_000L)

        assertEquals(1, events.size)
        assertEquals(PhonePrivateTimeline.Kind.CALL, events.single().kind)
        assertEquals("INCOMING", events.single().direction)
        assertFalse(events.single().toString().contains("device-bound-secret-fingerprint"))
    }

    @Test
    fun dropsFutureRecordsAndSanitizesSignalTokens() {
        val records = listOf(
            CallFilterDecisionEntity(
                occurredAtMs = 3_000L,
                action = "BLOCK",
                reason = "bad|reason\nraw",
                source = "USER",
                numberFingerprint = null
            ),
            CallFilterDecisionEntity(
                occurredAtMs = 5_000L,
                action = "ALLOW",
                reason = "NO_MATCHING_RULE",
                source = "NONE",
                numberFingerprint = null
            )
        )

        val events = CallHistoryTimelineAdapter.toEvents(records, 4_000L)

        assertEquals(1, events.size)
        assertEquals("BLOCK:badreasonraw:USER", events.single().signal)
    }
}
