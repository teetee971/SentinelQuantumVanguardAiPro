package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test

class SentinelAiApprovalPortsTest {

    @Test
    fun proposalPortDoesNotExposeApproval() {
        val owner = SentinelAiApprovalPorts.createOwner { 1000L }
        val methods = owner.proposals.javaClass.interfaces
            .flatMap { it.methods.toList() }
            .map { it.name }

        assertTrue(methods.contains("propose"))
        assertFalse(methods.contains("approve"))
        assertFalse(methods.contains("reject"))
        assertFalse(methods.contains("revokeAll"))
    }

    @Test
    fun proposalRequiresDecisionPort() {
        val owner = SentinelAiApprovalPorts.createOwner { 1000L }
        val session = owner.openSession()

        val proposal = owner.proposals.propose(
            session,
            "SCAN_LOCAL",
            "scope=local"
        )

        assertEquals("SCAN_LOCAL", proposal.actionId)
        assertTrue(owner.decisions.approve(proposal))
        assertFalse(owner.decisions.approve(proposal))
    }

    @Test
    fun revocationBlocksApproval() {
        val owner = SentinelAiApprovalPorts.createOwner { 1000L }
        val session = owner.openSession()

        val proposal = owner.proposals.propose(
            session,
            "SCAN_LOCAL",
            "scope=local"
        )

        owner.revokeSession(session)

        assertFalse(owner.decisions.approve(proposal))
    }

    @Test
    fun rejectedProposalDoesNotInvalidateUnrelatedProposal() {
        val owner = SentinelAiApprovalPorts.createOwner { 1000L }
        val session = owner.openSession()

        val first = owner.proposals.propose(session, "A", "1")
        val second = owner.proposals.propose(session, "B", "2")

        owner.decisions.reject(first)

        assertFalse(owner.decisions.approve(first))
        assertTrue(owner.decisions.approve(second))
    }
}
