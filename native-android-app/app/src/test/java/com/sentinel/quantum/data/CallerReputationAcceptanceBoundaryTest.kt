package com.sentinel.quantum.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
        val revocationStarted = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)

        try {
            val acceptance = workers.submit<String?> {
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
                revocationStarted.countDown()
                boundary.change { allowed = false }
            }
            assertTrue(revocationStarted.await(5, TimeUnit.SECONDS))
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
}
