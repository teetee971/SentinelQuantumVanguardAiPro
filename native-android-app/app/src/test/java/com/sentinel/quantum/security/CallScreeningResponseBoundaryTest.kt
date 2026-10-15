package com.sentinel.quantum.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallScreeningResponseBoundaryTest {
    @Test
    fun post_response_work_cannot_start_before_response_delivery() {
        val responseDelivered = AtomicBoolean(false)
        val maintenanceStarted = AtomicBoolean(false)
        val maintenanceFinished = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val boundary = CallScreeningResponseBoundary(executor::execute)
            boundary.deliverResponse { responseDelivered.set(true) }

            assertTrue(
                boundary.submitPostResponse {
                    maintenanceStarted.set(true)
                    assertTrue(responseDelivered.get())
                    maintenanceFinished.countDown()
                }
            )

            assertTrue(maintenanceFinished.await(1, TimeUnit.SECONDS))
            assertTrue(maintenanceStarted.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun submission_before_response_is_rejected_without_running_work() {
        val executor = Executors.newSingleThreadExecutor()
        val maintenanceStarted = AtomicBoolean(false)
        try {
            val boundary = CallScreeningResponseBoundary(executor::execute)

            assertFalse(
                boundary.submitPostResponse { maintenanceStarted.set(true) }
            )
            assertFalse(maintenanceStarted.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
