package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSubmissionGateTest {
    @Test fun rejectsSecondAdmissionUntilFirstSubmissionReleases() {
        val gate = SmsSubmissionGate()

        assertTrue(gate.tryAcquire())
        assertTrue(gate.inFlight.value)
        assertFalse(gate.tryAcquire())
        assertTrue(gate.inFlight.value)

        gate.release()
        assertFalse(gate.inFlight.value)

        assertTrue(gate.tryAcquire())
        gate.release()
    }

    @Test fun recreatedOwnerCannotBypassAnInFlightSubmission() {
        val firstOwner = SmsSubmissionGate()
        val recreatedOwner = SmsSubmissionGate()

        assertTrue(firstOwner.tryAcquire())
        assertFalse(recreatedOwner.tryAcquire())
        assertTrue(recreatedOwner.inFlight.value)

        firstOwner.release()

        assertTrue(recreatedOwner.tryAcquire())
        recreatedOwner.release()
    }

    @Test fun recreatedOwnerObservesAdmissionReleaseWithoutLocalStateMutation() {
        val firstOwner = SmsSubmissionGate()
        val recreatedOwner = SmsSubmissionGate()

        assertFalse(recreatedOwner.inFlight.value)
        assertTrue(firstOwner.tryAcquire())
        assertTrue(firstOwner.inFlight.value)
        assertTrue(recreatedOwner.inFlight.value)

        firstOwner.release()

        assertFalse(firstOwner.inFlight.value)
        assertFalse(recreatedOwner.inFlight.value)
    }
}
