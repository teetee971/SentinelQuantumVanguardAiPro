package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSubmissionGateTest {
    @Test fun rejectsSecondAdmissionUntilFirstSubmissionReleases() {
        val gate = SmsSubmissionGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())

        gate.release()

        assertTrue(gate.tryAcquire())
        gate.release()
    }

    @Test fun recreatedOwnerCannotBypassAnInFlightSubmission() {
        val firstOwner = SmsSubmissionGate()
        val recreatedOwner = SmsSubmissionGate()

        assertTrue(firstOwner.tryAcquire())
        assertFalse(recreatedOwner.tryAcquire())

        firstOwner.release()

        assertTrue(recreatedOwner.tryAcquire())
        recreatedOwner.release()
    }
}
