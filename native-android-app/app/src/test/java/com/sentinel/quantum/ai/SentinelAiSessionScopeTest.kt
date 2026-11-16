package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test

class SentinelAiSessionScopeTest {
    @Test fun closingScopeRevokesPendingAndApprovedActions() {
        val owner = testOwner(); val session = owner.openSessionScope()
        val pending = session.propose("A", "1")
        val approved = session.propose("B", "2")
        assertTrue(owner.decisions.approve(approved))
        session.close(); session.close()
        assertFalse(owner.decisions.approve(pending))
        assertEquals(SentinelAiApprovalAuthority.ExecutionResult.REJECTED, owner.decisions.execute(approved))
        assertThrows(IllegalStateException::class.java) { session.propose("A", "1") }
    }
    @Test fun closingOneScopePreservesOtherSessions() {
        val owner = testOwner(); val first = owner.openSessionScope(); val second = owner.openSessionScope()
        first.close(); val p = second.propose("A", "1")
        assertTrue(owner.decisions.approve(p))
        assertEquals(SentinelAiApprovalAuthority.ExecutionResult.EXECUTED, owner.decisions.execute(p))
        second.close()
    }
    @Test fun globalRevocationInvalidatesExistingScopeButAllowsNewSession() {
        val owner = testOwner(); val old = owner.openSessionScope(); owner.revokeAll()
        assertThrows(IllegalStateException::class.java) { old.propose("A", "1") }
        owner.openSessionScope().use { assertNotNull(it.propose("A", "1")) }
    }
    @Test fun lifecycleHandleExposesNoApprovalOrExecution() {
        val names = SentinelAiApprovalPorts.Session::class.java.declaredMethods.map { it.name }.toSet()
        assertEquals(setOf("propose", "close"), names)
    }
}
