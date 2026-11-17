package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SentinelAiProcessGateTest {
    @Test fun revocationInvalidatesEveryPreviouslyCapturedLease() {
        val gate = SentinelAiProcessGate();val first = gate.capture();val second = gate.capture()
        gate.revokeAll()
        assertFalse(gate.isActive(first));assertFalse(gate.isActive(second))
        assertThrows(IllegalStateException::class.java) { gate.execute(first) { fail("Effect after revocation") } }
        assertThrows(IllegalStateException::class.java) { gate.execute(second) { fail("Other instance executed") } }
    }
    @Test fun freshExplicitSessionDoesNotReactivateOldLease() {
        val gate = SentinelAiProcessGate();val old = gate.capture();gate.revokeAll();val fresh = gate.capture()
        assertEquals(42, gate.execute(fresh) { 42 });assertFalse(gate.isActive(old))
    }
    @Test fun revocationWaitsForAlreadyRunningSynchronousEffect() {
        val gate = SentinelAiProcessGate();val lease = gate.capture()
        val entered = CountDownLatch(1);val release = CountDownLatch(1);val revokerStarted = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val effect = pool.submit<Int> { gate.execute(lease) { entered.countDown();check(release.await(5,TimeUnit.SECONDS));7 } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val revoke = pool.submit { revokerStarted.countDown();gate.revokeAll() }
            assertTrue(revokerStarted.await(5, TimeUnit.SECONDS));assertFalse(revoke.isDone)
            release.countDown();assertEquals(7,effect.get(5,TimeUnit.SECONDS));revoke.get(5,TimeUnit.SECONDS)
            assertThrows(IllegalStateException::class.java) { gate.execute(lease) { fail("Late effect") } }
        } finally { release.countDown();pool.shutdownNow() }
    }
    @Test fun queuedOldLeasesNeverExecuteAfterEffectiveRevocation() {
        val gate = SentinelAiProcessGate();val leases = List(32) { gate.capture() };gate.revokeAll()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = leases.map { lease -> pool.submit<Boolean> {
                try { gate.execute(lease) { true } } catch (_: IllegalStateException) { false }
            } }
            assertTrue(tasks.none { it.get(5,TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
    }
    @Test fun failingEffectDoesNotPoisonLockOrInventSuccess() {
        val gate = SentinelAiProcessGate();val lease = gate.capture()
        assertThrows(java.io.IOException::class.java) { gate.execute(lease) { throw java.io.IOException("fixture") } }
        gate.revokeAll();assertFalse(gate.isActive(lease));assertEquals(1,gate.execute(gate.capture()) { 1 })
    }
    @Test fun reentrantRevocationCannotClaimCompletionInsideAnEffect() {
        val gate = SentinelAiProcessGate();val lease = gate.capture()
        gate.execute(lease) { assertThrows(IllegalStateException::class.java) { gate.revokeAll() } }
        assertTrue(gate.isActive(lease));gate.revokeAll();assertFalse(gate.isActive(lease))
    }
    @Test fun nestedEffectsAreRejected() {
        val gate = SentinelAiProcessGate();val lease = gate.capture()
        gate.execute(lease) { assertThrows(IllegalStateException::class.java) { gate.execute(lease) { fail("Nested effect") } } }
    }
}
