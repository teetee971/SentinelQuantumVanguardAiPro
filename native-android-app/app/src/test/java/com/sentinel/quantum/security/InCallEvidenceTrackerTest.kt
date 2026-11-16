package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InCallEvidenceTrackerTest {
    private data class CallRef(val number: Int)

    @Test
    fun removalDuringWriteRejectsLateCompletion() {
        val tracker = InCallEvidenceTracker<CallRef>()
        val call = CallRef(1)
        tracker.track(call)
        val token = requireNotNull(
            tracker.begin(call, InCallEvidenceTracker.Kind.INCOMING_NOTIFICATION)
        )

        tracker.remove(call)

        assertEquals(
            InCallEvidenceTracker.Completion.STALE,
            tracker.complete(token, stored = true)
        )
        assertFalse(tracker.isRecorded(call, InCallEvidenceTracker.Kind.INCOMING_NOTIFICATION))
        assertFalse(tracker.isInFlight(call, InCallEvidenceTracker.Kind.INCOMING_NOTIFICATION))
    }

    @Test
    fun destroyRejectsCallbacksThatFinishAfterServiceTeardown() {
        val tracker = InCallEvidenceTracker<CallRef>()
        val call = CallRef(1)
        tracker.track(call)
        val token = requireNotNull(
            tracker.begin(call, InCallEvidenceTracker.Kind.CONNECTED)
        )

        tracker.destroy()

        assertEquals(
            InCallEvidenceTracker.Completion.STALE,
            tracker.complete(token, stored = true)
        )
        assertFalse(tracker.isRecorded(call, InCallEvidenceTracker.Kind.CONNECTED))
    }

    @Test
    fun storageFailureClearsFlightAndAllowsAnExplicitRetry() {
        val tracker = InCallEvidenceTracker<CallRef>()
        val call = CallRef(1)
        tracker.track(call)
        val first = requireNotNull(
            tracker.begin(call, InCallEvidenceTracker.Kind.CONNECTED)
        )

        assertEquals(
            InCallEvidenceTracker.Completion.APPLIED,
            tracker.complete(first, stored = false)
        )
        assertFalse(tracker.isRecorded(call, InCallEvidenceTracker.Kind.CONNECTED))
        assertFalse(tracker.isInFlight(call, InCallEvidenceTracker.Kind.CONNECTED))

        val retry = tracker.begin(call, InCallEvidenceTracker.Kind.CONNECTED)
        assertNotNull(retry)
        assertEquals(
            InCallEvidenceTracker.Completion.STALE,
            tracker.complete(first, stored = true)
        )
        assertEquals(
            InCallEvidenceTracker.Completion.APPLIED,
            tracker.complete(requireNotNull(retry), stored = true)
        )
        assertTrue(tracker.isRecorded(call, InCallEvidenceTracker.Kind.CONNECTED))
    }

    @Test
    fun callbackFromRemovedGenerationCannotCompleteReaddedCall() {
        val tracker = InCallEvidenceTracker<CallRef>()
        val call = CallRef(1)
        tracker.track(call)
        val oldToken = requireNotNull(
            tracker.begin(call, InCallEvidenceTracker.Kind.CONNECTED)
        )

        tracker.remove(call)
        tracker.track(call)
        val currentToken = requireNotNull(
            tracker.begin(call, InCallEvidenceTracker.Kind.CONNECTED)
        )

        assertEquals(
            InCallEvidenceTracker.Completion.STALE,
            tracker.complete(oldToken, stored = true)
        )
        assertFalse(tracker.isRecorded(call, InCallEvidenceTracker.Kind.CONNECTED))
        assertTrue(tracker.isInFlight(call, InCallEvidenceTracker.Kind.CONNECTED))
        assertEquals(
            InCallEvidenceTracker.Completion.APPLIED,
            tracker.complete(currentToken, stored = true)
        )
        assertTrue(tracker.isRecorded(call, InCallEvidenceTracker.Kind.CONNECTED))
    }

    @Test
    fun simultaneousEqualCallsKeepTheirIdentityAndEvidenceSeparate() {
        val tracker = InCallEvidenceTracker<CallRef>()
        val incomingCall = CallRef(1)
        val connectedCall = CallRef(1)
        tracker.track(incomingCall)
        tracker.track(connectedCall)

        val incomingToken = requireNotNull(
            tracker.begin(incomingCall, InCallEvidenceTracker.Kind.INCOMING_NOTIFICATION)
        )
        val connectedToken = requireNotNull(
            tracker.begin(connectedCall, InCallEvidenceTracker.Kind.CONNECTED)
        )

        assertEquals(
            InCallEvidenceTracker.Completion.APPLIED,
            tracker.complete(incomingToken, stored = true)
        )
        assertTrue(
            tracker.isRecorded(incomingCall, InCallEvidenceTracker.Kind.INCOMING_NOTIFICATION)
        )
        assertFalse(tracker.isRecorded(connectedCall, InCallEvidenceTracker.Kind.CONNECTED))
        assertEquals(
            InCallEvidenceTracker.Completion.APPLIED,
            tracker.complete(connectedToken, stored = true)
        )
        assertTrue(tracker.isRecorded(connectedCall, InCallEvidenceTracker.Kind.CONNECTED))
    }

    @Test
    fun oneCallCanHaveIndependentEvidenceKindsInFlight() {
        val tracker = InCallEvidenceTracker<CallRef>()
        val call = CallRef(1)
        tracker.track(call)

        assertNotNull(tracker.begin(call, InCallEvidenceTracker.Kind.INCOMING_NOTIFICATION))
        assertNotNull(tracker.begin(call, InCallEvidenceTracker.Kind.CONNECTED))
        assertFalse(
            tracker.begin(call, InCallEvidenceTracker.Kind.INCOMING_NOTIFICATION) != null
        )
    }

    @Test
    fun concurrentBeginsForOneCallAllowOnlyOneWriteAttempt() {
        val tracker = InCallEvidenceTracker<CallRef>()
        val call = CallRef(1)
        tracker.track(call)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (0 until 2).map {
                executor.submit<InCallEvidenceTracker.Token<CallRef>?> {
                    start.await()
                    tracker.begin(call, InCallEvidenceTracker.Kind.CONNECTED)
                }
            }
            start.countDown()
            val tokens = futures.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, tokens.count { it != null })
        } finally {
            executor.shutdownNow()
        }
    }
}
