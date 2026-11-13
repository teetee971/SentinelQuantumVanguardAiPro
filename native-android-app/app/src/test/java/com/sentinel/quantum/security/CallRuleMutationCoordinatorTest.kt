package com.sentinel.quantum.security

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRuleMutationCoordinatorTest {
    @Test
    fun concurrent_mutations_share_one_process_lock_without_lost_updates() {
        val values = Collections.synchronizedSet(mutableSetOf<String>())
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit {
                ready.countDown()
                release.await(2, TimeUnit.SECONDS)
                CallRuleMutationCoordinator.withLock {
                    val next = values.toMutableSet()
                    next += "first"
                    values.clear()
                    values.addAll(next)
                }
            }
            val second = executor.submit {
                ready.countDown()
                release.await(2, TimeUnit.SECONDS)
                CallRuleMutationCoordinator.withLock {
                    val next = values.toMutableSet()
                    next += "second"
                    values.clear()
                    values.addAll(next)
                }
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            release.countDown()
            first.get(2, TimeUnit.SECONDS)
            second.get(2, TimeUnit.SECONDS)
            assertEquals(setOf("first", "second"), values)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun concurrent_replace_and_remove_are_serialized_as_complete_transactions() {
        val state = mutableSetOf("old-a", "old-b")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val replace = executor.submit {
                CallRuleMutationCoordinator.withLock {
                    state.clear()
                    state.addAll(setOf("new-a", "new-b"))
                }
            }
            val remove = executor.submit {
                CallRuleMutationCoordinator.withLock {
                    state.remove("old-a")
                }
            }
            replace.get(2, TimeUnit.SECONDS)
            remove.get(2, TimeUnit.SECONDS)
            assertEquals(setOf("new-a", "new-b"), state)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun worker_exception_is_propagated_to_the_junit_thread() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit {
                CallRuleMutationCoordinator.withLock {
                    error("mutation failed")
                }
            }
            try {
                future.get(2, TimeUnit.SECONDS)
                throw AssertionError("expected worker failure")
            } catch (error: ExecutionException) {
                assertEquals("mutation failed", error.cause?.message)
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
