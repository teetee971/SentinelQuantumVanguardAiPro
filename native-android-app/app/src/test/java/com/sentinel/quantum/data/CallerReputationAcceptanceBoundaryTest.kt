package com.sentinel.quantum.data

import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerReputationAcceptanceBoundaryTest {
    @Test fun revokedConsentRejectsPersistencePublicationAndUiResult() {
        val boundary = CallerReputationAcceptanceBoundary()
        var allowed = true
        var persistenceCalls = 0
        var publicationCalls = 0

        boundary.change { allowed = false }
        val displayed = boundary.accept(
            isEligible = { allowed },
            result = "remote reputation",
            persist = { persistenceCalls++; true },
            publish = { publicationCalls++ }
        )

        assertNull(displayed)
        assertEquals(0, persistenceCalls)
        assertEquals(0, publicationCalls)
    }

    @Test fun persistenceRejectionNeverPublishesOrReturnsUiResult() {
        val boundary = CallerReputationAcceptanceBoundary()
        var publicationCalls = 0

        val displayed = boundary.accept(
            isEligible = { true },
            result = "remote reputation",
            persist = { false },
            publish = { publicationCalls++ }
        )

        assertNull(displayed)
        assertEquals(0, publicationCalls)
    }

    @Test fun revocationCannotCompleteBetweenPersistenceAndPublication() {
        val boundary = CallerReputationAcceptanceBoundary()
        var allowed = true
        val publicationCalls = AtomicInteger()
        val persistenceEntered = CountDownLatch(1)
        val finishPersistence = CountDownLatch(1)
        val revocationRunnableEntered = CountDownLatch(1)
        val acceptanceThread = AtomicReference<Thread>()
        val revocationThread = AtomicReference<Thread>()
        val workers = Executors.newFixedThreadPool(2)

        try {
            val acceptance = workers.submit<String?> {
                acceptanceThread.set(Thread.currentThread())
                boundary.accept(
                    isEligible = { allowed },
                    result = "remote reputation",
                    persist = {
                        persistenceEntered.countDown()
                        finishPersistence.await(5, TimeUnit.SECONDS)
                    },
                    publish = { publicationCalls.incrementAndGet() }
                )
            }
            assertTrue(persistenceEntered.await(5, TimeUnit.SECONDS))
            val revocation = workers.submit {
                revocationThread.set(Thread.currentThread())
                revocationRunnableEntered.countDown()
                boundary.change { allowed = false }
            }
            assertTrue(revocationRunnableEntered.await(5, TimeUnit.SECONDS))
            assertTrue(
                "revocation must attempt the same held monitor before persistence resumes",
                awaitMonitorContention(revocationThread.get(), acceptanceThread.get())
            )
            assertFalse("revocation must wait for acceptance", revocation.isDone)

            finishPersistence.countDown()
            assertEquals("remote reputation", acceptance.get(5, TimeUnit.SECONDS))
            revocation.get(5, TimeUnit.SECONDS)
            assertFalse(allowed)
            assertNull(
                boundary.accept(
                    isEligible = { allowed },
                    result = "later result",
                    persist = { true },
                    publish = { publicationCalls.incrementAndGet() }
                )
            )
            assertEquals(1, publicationCalls.get())
        } finally {
            finishPersistence.countDown()
            workers.shutdownNow()
        }
    }

    @Test fun revocationDuringPreparationRejectsTheLaterUiResultAndPublication() {
        val boundary = CallerReputationAcceptanceBoundary()
        var allowed = true
        val preparationEntered = CountDownLatch(1)
        val finishPreparation = CountDownLatch(1)
        val publicationCalls = AtomicInteger()
        val workers = Executors.newSingleThreadExecutor()

        try {
            val acceptance = workers.submit<String?> {
                preparationEntered.countDown()
                if (!finishPreparation.await(5, TimeUnit.SECONDS)) return@submit null
                boundary.accept(
                    isEligible = { allowed },
                    result = "prepared reputation",
                    persist = { true },
                    publish = { publicationCalls.incrementAndGet() }
                )
            }
            assertTrue(preparationEntered.await(5, TimeUnit.SECONDS))
            boundary.change { allowed = false }
            finishPreparation.countDown()

            assertNull(acceptance.get(5, TimeUnit.SECONDS))
            assertEquals(0, publicationCalls.get())
        } finally {
            finishPreparation.countDown()
            workers.shutdownNow()
        }
    }

    @Suppress("DEPRECATION")
    private fun awaitMonitorContention(waiter: Thread, owner: Thread): Boolean {
        val monitor = ManagementFactory.getThreadMXBean()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val state = monitor.getThreadInfo(waiter.id) ?: return false
            if (state.threadState == Thread.State.BLOCKED && state.lockOwnerId == owner.id) {
                return true
            }
            Thread.yield()
        }
        return false
    }
}
