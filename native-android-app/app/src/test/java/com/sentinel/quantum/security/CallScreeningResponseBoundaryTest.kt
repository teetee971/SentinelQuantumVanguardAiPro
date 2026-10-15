package com.sentinel.quantum.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallScreeningResponseBoundaryTest {
    @Test fun submission_before_response_is_rejected_without_running_work() {
        val ran = AtomicBoolean(false)
        val boundary = CallScreeningResponseBoundary { command -> command.run() }

        assertFalse(boundary.submitPostResponse { ran.set(true) })
        assertFalse(ran.get())
    }

    @Test fun successful_response_allows_post_response_work() {
        val ran = AtomicBoolean(false)
        val boundary = CallScreeningResponseBoundary { command -> command.run() }

        boundary.deliverResponse { Unit }

        assertTrue(boundary.submitPostResponse { ran.set(true) })
        assertTrue(ran.get())
    }

    @Test fun throwing_response_never_opens_boundary() {
        val ran = AtomicBoolean(false)
        val boundary = CallScreeningResponseBoundary { command -> command.run() }

        runCatching { boundary.deliverResponse { error("platform response failed") } }

        assertFalse(boundary.submitPostResponse { ran.set(true) })
        assertFalse(ran.get())
    }

    @Test fun concurrent_submission_cannot_run_before_response_returns() {
        val executor = Executors.newSingleThreadExecutor()
        val responseEntered = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        val workRan = AtomicBoolean(false)
        val boundary = CallScreeningResponseBoundary(executor::execute)
        try {
            val responseThread = Thread {
                boundary.deliverResponse {
                    responseEntered.countDown()
                    releaseResponse.await(1, TimeUnit.SECONDS)
                }
            }
            responseThread.start()
            assertTrue(responseEntered.await(1, TimeUnit.SECONDS))

            assertFalse(boundary.submitPostResponse { workRan.set(true) })
            assertFalse(workRan.get())

            releaseResponse.countDown()
            responseThread.join(1_000)
            assertTrue(boundary.submitPostResponse { workRan.set(true) })
            executor.shutdown()
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS))
            assertTrue(workRan.get())
        } finally {
            releaseResponse.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun boundaries_are_isolated_per_call() {
        val first = CallScreeningResponseBoundary { command -> command.run() }
        val second = CallScreeningResponseBoundary { command -> command.run() }
        val secondWorkRan = AtomicBoolean(false)

        first.deliverResponse { Unit }

        assertFalse(second.submitPostResponse { secondWorkRan.set(true) })
        assertFalse(secondWorkRan.get())
    }
}
