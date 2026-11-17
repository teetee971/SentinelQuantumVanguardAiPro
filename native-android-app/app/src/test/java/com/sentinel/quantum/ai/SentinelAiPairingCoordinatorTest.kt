package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SentinelAiPairingCoordinatorTest {
    @Test fun forgottenPairingCannotBeRestoredByAnOlderRequest() {
        val gate = SentinelAiPairingCoordinator()
        val old = gate.capture()
        val forget = gate.beginForget()
        var stored = true
        gate.finishForget(forget) { stored = false }
        assertThrows(IllegalStateException::class.java) { gate.use(old) { stored = true } }
        assertFalse(stored)
        gate.use(gate.capture()) { stored = true }
        assertTrue(stored)
    }
    @Test fun sessionRevocationRejectsItsPendingSaveWithoutRevokingOtherSessions() {
        val gate = SentinelAiPairingCoordinator()
        val revoked = gate.capture(); val other = gate.capture()
        gate.revoke(revoked)
        assertThrows(IllegalStateException::class.java) { gate.use(revoked) { fail("Late save") } }
        assertEquals(42, gate.use(other) { 42 })
    }
    @Test fun forgetDeniesNewRequestsUntilDeletionSucceeds() {
        val gate = SentinelAiPairingCoordinator()
        val token = gate.beginForget()
        assertThrows(IllegalStateException::class.java) { gate.capture() }
        assertThrows(IllegalStateException::class.java) { gate.finishForget(token) { error("Disk error") } }
        assertThrows(IllegalStateException::class.java) { gate.capture() }
        gate.finishForget(gate.beginForget()) { }
        gate.use(gate.capture()) { }
    }
    @Test fun obsoleteForgetCompletionCannotUnlockANewerForget() {
        val gate = SentinelAiPairingCoordinator()
        val old = gate.beginForget();val current = gate.beginForget()
        assertThrows(IllegalStateException::class.java) { gate.finishForget(old) { fail("Obsolete delete") } }
        assertThrows(IllegalStateException::class.java) { gate.capture() }
        gate.finishForget(current) { }
        gate.capture()
    }
    @Test fun foreignTicketsAreRejected() {
        val first = SentinelAiPairingCoordinator();val second = SentinelAiPairingCoordinator()
        assertThrows(IllegalStateException::class.java) { second.use(first.capture()) { fail("Foreign save") } }
    }
    @Test fun forgetWaitsForEarlierSaveThenPreventsLateSaveAcrossThreads() {
        val gate = SentinelAiPairingCoordinator();val ticket = gate.capture()
        val entered = CountDownLatch(1);val release = CountDownLatch(1);val requested = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        var stored = false
        try {
            val save = pool.submit { gate.use(ticket) {
                entered.countDown();check(release.await(3, TimeUnit.SECONDS));stored = true
            } }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val forget = pool.submit {
                requested.countDown()
                val token = gate.beginForget()
                gate.finishForget(token) { stored = false }
            }
            assertTrue(requested.await(3, TimeUnit.SECONDS))
            assertFalse(forget.isDone)
            release.countDown();save.get(3, TimeUnit.SECONDS);forget.get(3, TimeUnit.SECONDS)
            assertFalse(stored)
            assertThrows(IllegalStateException::class.java) { gate.use(ticket) { stored = true } }
        } finally { release.countDown();pool.shutdownNow() }
    }
    @Test fun revokeWaitsForEarlierSaveBeforeAcknowledgingCompletion() {
        val gate = SentinelAiPairingCoordinator();val ticket = gate.capture()
        val entered = CountDownLatch(1);val release = CountDownLatch(1);val requested = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val save = pool.submit { gate.use(ticket) { entered.countDown();check(release.await(3,TimeUnit.SECONDS)) } }
            assertTrue(entered.await(3,TimeUnit.SECONDS))
            val revoke = pool.submit { requested.countDown();gate.revoke(ticket) }
            assertTrue(requested.await(3,TimeUnit.SECONDS));assertFalse(revoke.isDone)
            release.countDown();save.get(3,TimeUnit.SECONDS);revoke.get(3,TimeUnit.SECONDS)
            assertThrows(IllegalStateException::class.java) { gate.use(ticket) { fail("Save after revoke") } }
        } finally { release.countDown();pool.shutdownNow() }
    }
}
