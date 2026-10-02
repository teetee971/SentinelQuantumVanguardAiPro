package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsCallbackReplayPolicyTest {
    @Test
    fun `first callback is accepted`() {
        assertTrue(MmsCallbackReplayPolicy.shouldAccept(existingAtMs = null, nowMs = 10_000L))
    }

    @Test
    fun `callback replay inside ttl is rejected`() {
        assertFalse(MmsCallbackReplayPolicy.shouldAccept(existingAtMs = 10_000L, nowMs = 11_000L))
    }

    @Test
    fun `expired tombstone allows a later callback lifecycle`() {
        val first = 10_000L
        assertTrue(
            MmsCallbackReplayPolicy.shouldAccept(
                existingAtMs = first,
                nowMs = first + MmsCallbackReplayPolicy.TTL_MS + 1L
            )
        )
    }

    @Test
    fun `future tombstone fails closed`() {
        assertFalse(MmsCallbackReplayPolicy.shouldAccept(existingAtMs = 20_000L, nowMs = 10_000L))
    }

    @Test
    fun `expired and overflow entries are pruned while recent entries stay`() {
        val now = MmsCallbackReplayPolicy.TTL_MS + 100_000L
        val entries = linkedMapOf<String, Long>()
        entries["expired"] = 1L
        repeat(MmsCallbackReplayPolicy.MAX_ENTRIES) { index ->
            entries["recent-$index"] = now - (MmsCallbackReplayPolicy.MAX_ENTRIES - index).toLong()
        }

        val pruned = MmsCallbackReplayPolicy.keysToPrune(entries, now)

        assertTrue("expired" in pruned)
        assertTrue(pruned.any { it.startsWith("recent-") })
        assertFalse("recent-${MmsCallbackReplayPolicy.MAX_ENTRIES - 1}" in pruned)
    }
}
