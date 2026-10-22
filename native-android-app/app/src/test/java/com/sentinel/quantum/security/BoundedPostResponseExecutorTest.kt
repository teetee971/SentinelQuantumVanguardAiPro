package com.sentinel.quantum.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BoundedPostResponseExecutorTest {
    @Test fun defaultQueueCapacityIsBounded() {
        val executor = BoundedPostResponseExecutor.create("bounded-post-response-test")
        try {
            assertEquals(BoundedPostResponseExecutor.DEFAULT_QUEUE_CAPACITY, executor.queue.remainingCapacity())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun overloadRejectsOptionalWorkInsteadOfGrowingWithoutBound() {
        val executor = BoundedPostResponseExecutor.create(
            threadName = "bounded-post-response-overload-test",
            queueCapacity = 1
        )
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        try {
            executor.execute {
                workerStarted.countDown()
                releaseWorker.await()
            }
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS))

            // One queued task fills the only waiting slot while the worker remains blocked.
            executor.execute { Unit }
            assertEquals(0, executor.queue.remainingCapacity())

            try {
                executor.execute { Unit }
                fail("Expected bounded post-response executor to reject work when saturated")
            } catch (_: RejectedExecutionException) {
                // Expected: screening already responded, so optional post-response work may be dropped.
            }
        } finally {
            releaseWorker.countDown()
            executor.shutdownNow()
        }
    }
    @Test fun productionQueueCapacityRejectsAt128PendingTasks() {
        val executor = BoundedPostResponseExecutor.create(
            threadName = "call-history-capacity-test",
            queueCapacity = 128
        )
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        try {
            executor.execute {
                workerStarted.countDown()
                releaseWorker.await()
            }
            assertTrue("Worker did not start", workerStarted.await(3, TimeUnit.SECONDS))
            repeat(128) { executor.execute { Unit } }
            assertEquals(128, executor.queue.size)
            assertEquals(0, executor.queue.remainingCapacity())
            try {
                executor.execute { Unit }
                fail("The 129th pending history task must be rejected")
            } catch (_: RejectedExecutionException) {
                assertEquals(128, executor.queue.size)
            }
        } finally {
            releaseWorker.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun queuedTasksExecuteInFifoOrderAfterWorkerRelease() {
        val executor = BoundedPostResponseExecutor.create(
            threadName = "call-history-fifo-test",
            queueCapacity = 4
        )
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(4)
        val observed = Collections.synchronizedList(mutableListOf<Int>())
        try {
            executor.execute {
                started.countDown()
                release.await()
            }
            assertTrue("Worker did not start", started.await(3, TimeUnit.SECONDS))
            repeat(4) { index ->
                executor.execute {
                    observed.add(index)
                    finished.countDown()
                }
            }
            release.countDown()
            assertTrue("Queued work did not finish", finished.await(5, TimeUnit.SECONDS))
            assertEquals(listOf(0, 1, 2, 3), observed.toList())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun rejectedQueuedWorkNeverExecutesAfterCapacityBecomesAvailable() {
        val executor = BoundedPostResponseExecutor.create(
            threadName = "call-history-rejection-test",
            queueCapacity = 1
        )
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queuedFinished = CountDownLatch(1)
        val rejectedExecuted = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            executor.execute {
                started.countDown()
                release.await()
            }
            assertTrue("Worker did not start", started.await(3, TimeUnit.SECONDS))
            executor.execute { queuedFinished.countDown() }
            try {
                executor.execute { rejectedExecuted.set(true) }
                fail("Saturated queue must reject optional task")
            } catch (_: RejectedExecutionException) {
                // Expected; this rejected callback must never run later.
            }
            release.countDown()
            assertTrue("Accepted queued work did not finish", queuedFinished.await(5, TimeUnit.SECONDS))
            assertTrue("Rejected callback was executed", !rejectedExecuted.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

}
