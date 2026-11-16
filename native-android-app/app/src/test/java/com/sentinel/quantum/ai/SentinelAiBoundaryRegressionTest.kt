package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test

/** These tests also compile against the transferred pre-fix API. */
class SentinelAiBoundaryRegressionTest {
    @Test fun unconfiguredAuthorityMustNotIssueArbitraryAction() {
        val a = SentinelAiApprovalAuthority({ 1000L }); val s = a.openSession()
        assertThrows(IllegalArgumentException::class.java) { a.proposalPort().propose(s, "DELETE_ALL", "*") }
    }
    @Test fun clockFailureMustInvalidateExistingControllerCapabilities() {
        var broken = false
        val c = SentinelAiSecureController { if (broken) error("unavailable") else 1000L }
        val s = c.openSession(); val p = c.request(s, "SCAN_LOCAL", "scope=local")
        broken = true
        assertThrows(IllegalStateException::class.java) { c.confirm(p.token, s, p.actionId, p.parameters) }
        broken = false
        assertEquals(0, c.activeSessionCount())
        assertThrows(IllegalStateException::class.java) { c.openSession() }
    }
    @Test fun gateMustNotAcceptTimeRollbackRelativeToOtherRequests() {
        val gate = SentinelAiActionGate()
        val p = gate.request("SCAN_LOCAL", "scope=local", 1000L)
        gate.request("SCAN_LOCAL", "scope=local", 1100L)
        assertThrows(IllegalStateException::class.java) { gate.confirm(p.token, p.actionId, p.parameters, 1050L) }
    }
    @Test fun controllerMustRejectOversizedParameters() {
        val c = SentinelAiSecureController { 1000L }; val s = c.openSession()
        assertThrows(IllegalArgumentException::class.java) { c.request(s, "SCAN_LOCAL", "x".repeat(4097)) }
    }
}
