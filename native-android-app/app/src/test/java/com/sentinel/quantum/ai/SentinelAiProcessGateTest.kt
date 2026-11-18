package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
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

    @Test fun requestRevocationBlocksNewWorkBeforeCompletion() {
        val gate = SentinelAiProcessGate()
        val old = gate.capture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val effect = pool.submit {
                gate.execute(old) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))

            // Must invalidate leases without waiting for the running effect.
            val revocation = gate.requestRevocation()
            assertTrue(gate.isRevoking())
            assertFalse(gate.isActive(old))

            assertThrows(IllegalStateException::class.java) {
                gate.capture()
            }

            release.countDown()
            effect.get(5, TimeUnit.SECONDS)
            gate.awaitRevocation(revocation)

            assertFalse(gate.isRevoking())
            assertFalse(gate.isActive(old))
            assertEquals(7, gate.execute(gate.capture()) { 7 })
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun revokeAllInvalidatesLeasesWhileWaitingForAnEffect() {
        val gate = SentinelAiProcessGate()
        val lease = gate.capture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val revoker = CompletableFuture<Thread>()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val effect = pool.submit { gate.execute(lease) { entered.countDown(); release.await() } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val revocation = pool.submit { revoker.complete(Thread.currentThread()); gate.revokeAll() }
            awaitGateWait(revoker.get(5, TimeUnit.SECONDS))
            assertFalse("The old lease must already be invalid", gate.isActive(lease))
            assertThrows(IllegalStateException::class.java) { gate.capture() }
            assertFalse(revocation.isDone)

            release.countDown()
            effect.get(5, TimeUnit.SECONDS)
            revocation.get(5, TimeUnit.SECONDS)
            assertFalse(gate.isActive(lease))
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun executionAlreadyQueuedAtRevocationCannotEnterItsEffect() {
        val gate = SentinelAiProcessGate()
        val running = gate.capture()
        val queued = gate.capture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val waiter = CompletableFuture<Thread>()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val effect = pool.submit { gate.execute(running) { entered.countDown(); release.await() } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val attempt = pool.submit {
                waiter.complete(Thread.currentThread())
                assertThrows(IllegalStateException::class.java) {
                    gate.execute(queued) { fail("A queued stale lease executed") }
                }
            }
            awaitGateWait(waiter.get(5, TimeUnit.SECONDS))
            val revocation = gate.requestRevocation()
            assertFalse(gate.isActive(queued))
            attempt.get(5, TimeUnit.SECONDS)
            assertFalse(effect.isDone)

            release.countDown()
            effect.get(5, TimeUnit.SECONDS)
            gate.awaitRevocation(revocation)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun executionSubmittedDuringRevocationIsDeniedWithoutWaitingForTheEffect() {
        val gate = SentinelAiProcessGate()
        val lease = gate.capture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val effect = pool.submit { gate.execute(lease) { entered.countDown(); release.await() } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val revocation = gate.requestRevocation()
            val attempt = pool.submit {
                assertThrows(IllegalStateException::class.java) {
                    gate.execute(lease) { fail("An effect was admitted during revocation") }
                }
            }
            attempt.get(5, TimeUnit.SECONDS)
            assertFalse(effect.isDone)

            release.countDown()
            effect.get(5, TimeUnit.SECONDS)
            gate.awaitRevocation(revocation)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun simultaneousRequestsShareTheTicketAndBothAcknowledgeAfterTheEffect() {
        val gate = SentinelAiProcessGate()
        val lease = gate.capture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val start = CountDownLatch(1)
        val tickets = List(2) { CompletableFuture<SentinelAiProcessGate.Revocation>() }
        val pool = Executors.newFixedThreadPool(3)
        try {
            val effect = pool.submit {
                gate.execute(lease) { entered.countDown(); release.await(); finished.countDown() }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val revocations = tickets.map { published ->
                pool.submit {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    val ticket = gate.requestRevocation()
                    published.complete(ticket)
                    gate.awaitRevocation(ticket)
                    assertEquals(0L, finished.count)
                }
            }
            start.countDown()
            val first = tickets[0].get(5, TimeUnit.SECONDS)
            assertSame(first, tickets[1].get(5, TimeUnit.SECONDS))
            assertTrue(revocations.none { it.isDone })
            assertThrows(IllegalStateException::class.java) { gate.capture() }

            release.countDown()
            effect.get(5, TimeUnit.SECONDS)
            revocations.forEach { it.get(5, TimeUnit.SECONDS) }
            assertFalse(gate.isActive(lease))
            assertTrue(gate.isActive(gate.capture()))
        } finally {
            start.countDown()
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun onlyTheExactTicketCanAcknowledgeTheCurrentCycle() {
        val gate = SentinelAiProcessGate()
        val old = gate.requestRevocation()
        gate.awaitRevocation(old)
        gate.awaitRevocation(old)
        val lease = gate.capture()
        val current = gate.requestRevocation()
        val other = SentinelAiProcessGate()
        val foreign = other.requestRevocation()

        assertThrows(IllegalStateException::class.java) { gate.awaitRevocation(old) }
        assertThrows(IllegalStateException::class.java) { gate.awaitRevocation(foreign) }
        assertThrows(IllegalStateException::class.java) { other.awaitRevocation(current) }
        assertThrows(IllegalStateException::class.java) {
            gate.awaitRevocation(SentinelAiProcessGate.Revocation(current.stamp))
        }
        assertThrows(IllegalStateException::class.java) { gate.capture() }
        assertThrows(IllegalStateException::class.java) { other.capture() }

        other.awaitRevocation(foreign)
        gate.awaitRevocation(current)
        assertThrows(IllegalStateException::class.java) { gate.awaitRevocation(old) }
        assertFalse(gate.isActive(lease))
        assertEquals(9, gate.execute(gate.capture()) { 9 })
    }

    @Test fun reentrantSplitRequestCannotStrandTheGateInRevocation() {
        val gate = SentinelAiProcessGate()
        val lease = gate.capture()
        gate.execute(lease) {
            assertThrows(IllegalStateException::class.java) { gate.requestRevocation() }
            assertTrue(gate.isActive(lease))
        }
        gate.revokeAll()
        assertFalse(gate.isActive(lease))
    }

    @Test fun reentrantAwaitCannotAcknowledgeAnExternalRequest() {
        val gate = SentinelAiProcessGate()
        val lease = gate.capture()
        val entered = CountDownLatch(1)
        val requested = CompletableFuture<SentinelAiProcessGate.Revocation>()
        val pool = Executors.newSingleThreadExecutor()
        try {
            val effect = pool.submit {
                gate.execute(lease) {
                    entered.countDown()
                    val ticket = requested.get(5, TimeUnit.SECONDS)
                    assertThrows(IllegalStateException::class.java) { gate.awaitRevocation(ticket) }
                    assertThrows(IllegalStateException::class.java) { gate.capture() }
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val ticket = gate.requestRevocation()
            requested.complete(ticket)
            effect.get(5, TimeUnit.SECONDS)
            assertThrows(IllegalStateException::class.java) { gate.capture() }
            gate.awaitRevocation(ticket)
            assertFalse(gate.isActive(lease))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun anEffectThrowingDuringRevocationStillDrainsAndCannotBeRetried() {
        val gate = SentinelAiProcessGate()
        val lease = gate.capture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val revoker = CompletableFuture<Thread>()
        val failure = IOException("fixture")
        val pool = Executors.newFixedThreadPool(2)
        try {
            val effect = pool.submit<Unit> { gate.execute(lease) { entered.countDown(); release.await(); throw failure } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val ticket = gate.requestRevocation()
            val revocation = pool.submit { revoker.complete(Thread.currentThread()); gate.awaitRevocation(ticket) }
            awaitGateWait(revoker.get(5, TimeUnit.SECONDS))
            assertFalse(revocation.isDone)
            assertThrows(IllegalStateException::class.java) { gate.capture() }

            release.countDown()
            assertSame(failure, assertThrows(ExecutionException::class.java) { effect.get(5, TimeUnit.SECONDS) }.cause)
            revocation.get(5, TimeUnit.SECONDS)
            assertThrows(IllegalStateException::class.java) { gate.execute(lease) { fail("Stale effect retried") } }
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun interruptedDrainKeepsTheGateClosedUntilAnotherWaiterCompletesIt() {
        val gate = SentinelAiProcessGate()
        val lease = gate.capture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val revoker = CompletableFuture<Thread>()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val effect = pool.submit { gate.execute(lease) { entered.countDown(); release.await() } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val ticket = gate.requestRevocation()
            val interrupted = pool.submit {
                revoker.complete(Thread.currentThread())
                assertThrows(InterruptedException::class.java) { gate.awaitRevocation(ticket) }
            }
            val worker = revoker.get(5, TimeUnit.SECONDS)
            awaitGateWait(worker)
            worker.interrupt()
            interrupted.get(5, TimeUnit.SECONDS)
            assertThrows(IllegalStateException::class.java) { gate.capture() }

            release.countDown()
            effect.get(5, TimeUnit.SECONDS)
            assertThrows(IllegalStateException::class.java) { gate.capture() }
            gate.awaitRevocation(gate.requestRevocation())
            assertFalse(gate.isActive(lease))
            assertEquals(3, gate.execute(gate.capture()) { 3 })
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    private fun awaitGateWait(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val state = thread.state
            if ((state == Thread.State.BLOCKED || state == Thread.State.WAITING) &&
                thread.stackTrace.any { it.className == SentinelAiProcessGate::class.java.name }
            ) return
            Thread.yield()
        }
        fail("Worker did not wait in the process gate")
    }
}
