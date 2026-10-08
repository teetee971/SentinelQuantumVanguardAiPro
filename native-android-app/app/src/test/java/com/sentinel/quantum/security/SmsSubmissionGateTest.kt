package com.sentinel.quantum.security

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsSubmissionGateTest {
    @Test
    fun onlyOneSubmissionMayBeInFlight() {
        val gate = SmsSubmissionGate()

        val first = gate.tryAcquire()

        assertNotNull(first)
        assertNull(gate.tryAcquire())
    }

    @Test
    fun releasingPermitAllowsTheNextSubmission() {
        val gate = SmsSubmissionGate()
        val first = requireNotNull(gate.tryAcquire())

        first.release()

        assertNotNull(gate.tryAcquire())
    }

    @Test
    fun stalePermitCannotReleaseANewerSubmission() {
        val gate = SmsSubmissionGate()
        val first = requireNotNull(gate.tryAcquire())
        first.release()
        val second = requireNotNull(gate.tryAcquire())

        first.release()

        assertNull(gate.tryAcquire())
        second.release()
    }
}
