package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test

class SentinelAiApprovalBrokerTest {

    @Test
    fun proposalMatchesRequestedAction() {
        val controller = SentinelAiSecureController { 1000L }
        val broker = SentinelAiApprovalBroker(controller)
        val session = controller.openSession()

        val proposal = broker.propose(
            session,
            "SCAN_LOCAL",
            "scope=local"
        )

        assertEquals(session, proposal.sessionId)
        assertEquals("SCAN_LOCAL", proposal.actionId)
        assertEquals("scope=local", proposal.parameters)
        assertEquals(1, controller.pendingCount())
    }

    @Test
    fun revokedSessionCannotPropose() {
        val controller = SentinelAiSecureController { 1000L }
        val broker = SentinelAiApprovalBroker(controller)
        val session = controller.openSession()

        controller.revokeSession(session)

        try {
            broker.propose(session, "SCAN_LOCAL", "")
            fail("Revoked session accepted")
        } catch (expected: IllegalStateException) {
            assertEquals(0, controller.pendingCount())
        }
    }

    @Test
    fun brokerDoesNotExposeConfirmationMethod() {
        val methods = SentinelAiApprovalBroker::class.java
            .declaredMethods
            .map { it.name }

        assertFalse(methods.contains("confirm"))
        assertFalse(methods.contains("approve"))
        assertFalse(methods.contains("execute"))
    }

    @Test
    fun proposalCannotBeReusedAfterControllerRevocation() {
        val controller = SentinelAiSecureController { 1000L }
        val broker = SentinelAiApprovalBroker(controller)
        val session = controller.openSession()

        val proposal = broker.propose(
            session,
            "SCAN_LOCAL",
            "scope=local"
        )

        controller.revokeAll()

        assertFalse(
            controller.confirm(
                proposal.token,
                proposal.sessionId,
                proposal.actionId,
                proposal.parameters
            )
        )
    }
}
