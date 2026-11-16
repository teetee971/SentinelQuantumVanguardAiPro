package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test

/** Denial/cancellation must not throw because the monotonic clock became unavailable. */
class SentinelAiDenialClockFailureTest {
    private var clockBroken = false
    private var authenticated = false
    private var effects = 0
    private val authority = SentinelAiApprovalAuthority(
        { if (clockBroken) error("Monotonic clock unavailable") else 1000L },
        mapOf("LOCAL" to SentinelAiActionPolicy({ it == "bounded" }, { true }, { effects++ })),
        { authenticated }
    )
    private fun proposal() = authority.proposalPort().propose(authority.openSession(), "LOCAL", "bounded")
    private fun assertInvalidated(p: SentinelAiApprovalAuthority.Proposal) {
        clockBroken = false
        authenticated = true
        assertFalse(authority.decisionPort().approve(p))
        assertEquals(SentinelAiApprovalAuthority.ExecutionResult.REJECTED, authority.decisionPort().execute(p))
        assertThrows(IllegalStateException::class.java) { authority.openSession() }
        assertEquals(0, effects)
    }

    @Test fun authenticationDenialWithBrokenClockReturnsFalseAndRevokesOtherGrants() {
        val denied = proposal()
        val other = proposal()
        authenticated = true
        assertTrue(authority.decisionPort().approve(other))
        authenticated = false
        clockBroken = true
        assertFalse(authority.decisionPort().approve(denied))
        assertInvalidated(other)
    }

    @Test fun unapprovedExecutionWithBrokenClockReturnsRejected() {
        val p = proposal()
        clockBroken = true
        assertEquals(SentinelAiApprovalAuthority.ExecutionResult.REJECTED, authority.decisionPort().execute(p))
        assertInvalidated(p)
    }

    @Test fun explicitRejectionWithBrokenClockCompletesAndInvalidates() {
        val p = proposal()
        clockBroken = true
        authority.decisionPort().reject(p)
        assertInvalidated(p)
    }
}
