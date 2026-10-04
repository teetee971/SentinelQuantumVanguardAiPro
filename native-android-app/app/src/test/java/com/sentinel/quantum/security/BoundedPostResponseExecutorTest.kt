package com.sentinel.quantum.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
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
                releaseWorker.await(2, TimeUnit.SECONDS)
            }
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS))

            // One queued task fills the only waiting slot while the worker is occupied.
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
}
