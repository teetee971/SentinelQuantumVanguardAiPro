package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SentinelAiSecureControllerTest {

    @Test
    fun validApprovalIsSingleUse() {
        val c = SentinelAiSecureController { 1000L }
        val s = c.openSession()
        val a = c.request(s, "setting", "on")
        assertTrue(c.confirm(a.token, s, "setting", "on"))
        assertFalse(c.confirm(a.token, s, "setting", "on"))
    }

    @Test
    fun expiredApprovalIsRejected() {
        var now = 1000L
        val c = SentinelAiSecureController { now }
        val s = c.openSession()
        val a = c.request(s, "setting", "on", 100L)
        now = 1100L
        assertFalse(c.confirm(a.token, s, "setting", "on"))
    }

    @Test
    fun wrongSessionIsRejected() {
        val c = SentinelAiSecureController { 1000L }
        val s1 = c.openSession()
        val s2 = c.openSession()
        val a = c.request(s1, "setting", "on")
        assertFalse(c.confirm(a.token, s2, "setting", "on"))
    }

    @Test
    fun revokedSessionIsRejected() {
        val c = SentinelAiSecureController { 1000L }
        val s = c.openSession()
        val a = c.request(s, "setting", "on")
        c.revokeSession(s)
        assertFalse(c.confirm(a.token, s, "setting", "on"))
        assertThrows(IllegalStateException::class.java) {
            c.request(s, "setting", "on")
        }
    }

    @Test
    fun revokeAllMustNotReactivateRevokedSession() {
        val c = SentinelAiSecureController { 1000L }
        val s = c.openSession()
        c.revokeSession(s)
        c.revokeAll()
        assertThrows(IllegalStateException::class.java) {
            c.request(s, "setting", "on")
        }
    }

    @Test
    fun capacityIsBounded() {
        val c = SentinelAiSecureController { 1000L }
        val s = c.openSession()
        repeat(SentinelAiSecureController.MAX_PENDING) {
            c.request(s, "setting", "value-$it")
        }
        assertEquals(SentinelAiSecureController.MAX_PENDING, c.pendingCount())
        assertThrows(IllegalStateException::class.java) {
            c.request(s, "setting", "overflow")
        }
    }

    @Test
    fun expiredEntriesReleaseCapacity() {
        var now = 1000L
        val c = SentinelAiSecureController { now }
        val s = c.openSession()
        repeat(SentinelAiSecureController.MAX_PENDING) {
            c.request(s, "setting", "value-$it", 10L)
        }
        now = 1010L
        c.request(s, "setting", "new")
        assertEquals(1, c.pendingCount())
    }

    @Test
    fun clockRollbackIsRejected() {
        var now = 1000L
        val c = SentinelAiSecureController { now }
        val s = c.openSession()
        val a = c.request(s, "setting", "on")
        now = 999L
        assertThrows(IllegalStateException::class.java) {
            c.confirm(a.token, s, "setting", "on")
        }
        assertEquals(0, c.activeSessionCount())
    }

    @Test
    fun revokedSessionTrackingMustBeBounded() {
        val c = SentinelAiSecureController { 1000L }
        repeat(10001) {
            c.revokeSession("unknown-$it")
        }
        assertEquals(0, c.activeSessionCount())
        assertEquals(0, c.pendingCount())
    }

    @Test
    fun unknownSessionCannotRequest() {
        val c = SentinelAiSecureController { 1000L }
        assertThrows(IllegalStateException::class.java) {
            c.request("invented", "setting", "on")
        }
    }

    @Test
    fun globalRevocationInvalidatesAllApprovals() {
        val c = SentinelAiSecureController { 1000L }
        val s = c.openSession()
        val a = c.request(s, "setting", "on")
        c.revokeAll()
        assertFalse(c.confirm(a.token, s, "setting", "on"))
        assertThrows(IllegalStateException::class.java) {
            c.request(s, "setting", "on")
        }
    }

    @Test
    fun activeSessionsAreBounded() {
        val c = SentinelAiSecureController { 1000L }
        repeat(SentinelAiSecureController.MAX_SESSIONS) {
            c.openSession()
        }
        assertThrows(IllegalStateException::class.java) {
            c.openSession()
        }
    }

    @Test
    fun wrongParametersConsumeApproval() {
        val c = SentinelAiSecureController { 1000L }
        val s = c.openSession()
        val a = c.request(s, "setting", "on")
        assertFalse(c.confirm(a.token, s, "setting", "off"))
        assertFalse(c.confirm(a.token, s, "setting", "on"))
    }

    @Test
    fun concurrentConfirmationIsSingleUse() {
        val c = SentinelAiSecureController { 1000L }
        val s = c.openSession()
        val a = c.request(s, "setting", "on")
        val workers = 8
        val pool = Executors.newFixedThreadPool(workers)
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val successes = AtomicInteger(0)

        try {
            repeat(workers) {
                pool.execute {
                    try {
                        ready.countDown()
                        start.await()
                        if (c.confirm(a.token, s, "setting", "on")) {
                            successes.incrementAndGet()
                        }
                    } finally {
                        done.countDown()
                    }
                }
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals(1, successes.get())
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun clockRollbackCannotBeClearedByRevokeAll() {
        var now = 1000L
        val c = SentinelAiSecureController { now }
        c.openSession()
        now = 999L

        assertThrows(IllegalStateException::class.java) {
            c.openSession()
        }

        c.revokeAll()

        assertThrows(IllegalStateException::class.java) {
            c.openSession()
        }
    }
}
