package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class SentinelAiActionGateTest {

    @Test
    fun matchingConfirmationSucceeds() {
        val gate = SentinelAiActionGate()
        val request = gate.request("setting", "value=true", 100L)

        assertTrue(
            gate.confirm(request.token, "setting", "value=true", 101L)
        )
    }

    @Test
    fun replayIsRejected() {
        val gate = SentinelAiActionGate()
        val request = gate.request("setting", "x", 100L)

        assertTrue(gate.confirm(request.token, "setting", "x", 101L))
        assertFalse(gate.confirm(request.token, "setting", "x", 102L))
    }

    @Test
    fun wrongActionIsRejected() {
        val gate = SentinelAiActionGate()
        val request = gate.request("read", "x", 100L)

        assertFalse(gate.confirm(request.token, "delete", "x", 101L))
        assertFalse(gate.confirm(request.token, "read", "x", 102L))
    }

    @Test
    fun changedParametersAreRejected() {
        val gate = SentinelAiActionGate()
        val request = gate.request("setting", "enabled=false", 100L)

        assertFalse(
            gate.confirm(request.token, "setting", "enabled=true", 101L)
        )
    }

    @Test
    fun expiredRequestIsRejected() {
        val gate = SentinelAiActionGate()
        val request = gate.request("setting", "x", 100L, 10L)

        assertFalse(gate.confirm(request.token, "setting", "x", 110L))
    }

    @Test
    fun revokedRequestIsRejected() {
        val gate = SentinelAiActionGate()
        val request = gate.request("setting", "x", 100L)

        gate.revokeAll()

        assertFalse(gate.confirm(request.token, "setting", "x", 101L))
        assertEquals(0, gate.pendingCount())
    }

    @Test
    fun unknownTokenIsRejected() {
        val gate = SentinelAiActionGate()

        assertFalse(gate.confirm("unknown", "setting", "x", 100L))
    }

    @Test
    fun invalidLifetimeIsRejected() {
        val gate = SentinelAiActionGate()

        try {
            gate.request("setting", "x", 100L, 0L)
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun timestampOverflowIsRejected() {
        val gate = SentinelAiActionGate()

        try {
            gate.request("setting", "x", Long.MAX_VALUE, 30_000L)
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun concurrentConfirmationSucceedsExactlyOnce() {
        val gate = SentinelAiActionGate()
        val request = gate.request("setting", "x", 100L)

        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val success = AtomicInteger(0)

        val workers = (1..8).map {
            thread(start = true) {
                ready.countDown()
                start.await()
                if (gate.confirm(request.token, "setting", "x", 101L)) {
                    success.incrementAndGet()
                }
            }
        }

        ready.await()
        start.countDown()
        workers.forEach { it.join() }

        assertEquals(1, success.get())
    }
}
