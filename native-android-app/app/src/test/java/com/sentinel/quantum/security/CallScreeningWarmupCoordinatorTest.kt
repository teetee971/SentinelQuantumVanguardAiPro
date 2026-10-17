package com.sentinel.quantum.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallScreeningWarmupCoordinatorTest {
    @Test fun start_is_idempotent_and_loader_runs_once_when_ready() {
        val executor = Executors.newSingleThreadExecutor()
        val loads = AtomicInteger(0)
        val coordinator = CallScreeningWarmupCoordinator(
            loader = { loads.incrementAndGet() },
            dispatch = executor::execute
        )
        try {
            assertTrue(coordinator.start())
            assertFalse(coordinator.start())
            assertTrue(coordinator.awaitReady(1_000))
            assertFalse(coordinator.start())
            assertEquals(1, loads.get())
            assertEquals(CallScreeningWarmupCoordinator.State.READY, coordinator.state())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun await_is_strictly_bounded_and_fails_open_while_loader_is_blocked() {
        val executor = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val coordinator = CallScreeningWarmupCoordinator(
            loader = {
                entered.countDown()
                release.await(1, TimeUnit.SECONDS)
            },
            dispatch = executor::execute
        )
        try {
            assertTrue(coordinator.start())
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val startedAt = System.nanoTime()
            assertFalse(coordinator.awaitReady(25))
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            assertTrue("wait exceeded bounded budget: ${elapsedMs}ms", elapsedMs < 250)
            release.countDown()
            assertTrue(coordinator.awaitReady(1_000))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun transient_loader_failure_can_retry_and_reach_ready() {
        val executor = Executors.newSingleThreadExecutor()
        val attempts = AtomicInteger(0)
        val coordinator = CallScreeningWarmupCoordinator(
            loader = {
                if (attempts.incrementAndGet() == 1) error("storage temporarily unavailable")
            },
            dispatch = executor::execute
        )
        try {
            assertTrue(coordinator.start())
            assertFalse(coordinator.awaitReady(1_000))
            assertEquals(CallScreeningWarmupCoordinator.State.FAILED, coordinator.state())

            assertTrue(coordinator.start())
            assertTrue(coordinator.awaitReady(1_000))
            assertEquals(2, attempts.get())
            assertEquals(CallScreeningWarmupCoordinator.State.READY, coordinator.state())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun dispatcher_failure_can_be_retried() {
        val dispatchAttempts = AtomicInteger(0)
        val coordinator = CallScreeningWarmupCoordinator(
            loader = { Unit },
            dispatch = { command ->
                if (dispatchAttempts.incrementAndGet() == 1) {
                    throw IllegalStateException("executor rejected")
                }
                command.run()
            }
        )

        assertFalse(coordinator.start())
        assertFalse(coordinator.awaitReady(10))
        assertEquals(CallScreeningWarmupCoordinator.State.FAILED, coordinator.state())
        assertTrue(coordinator.start())
        assertTrue(coordinator.awaitReady(10))
        assertEquals(CallScreeningWarmupCoordinator.State.READY, coordinator.state())
    }
}
