package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test

class SentinelAiApprovalAuthorityTest {

    @Test
    fun rejectingOneProposalPreservesAnother() {
        val authority = SentinelAiApprovalAuthority { 1000L }
        val session = authority.openSession()
        val proposals = authority.proposalPort()
        val decisions = authority.decisionPort()

        val first = proposals.propose(session, "ACTION_A", "a=1")
        val second = proposals.propose(session, "ACTION_B", "b=2")

        decisions.reject(first)

        assertFalse(decisions.approve(first))
        assertTrue(decisions.approve(second))
    }

    @Test
    fun rejectedProposalCannotBeApproved() {
        val authority = SentinelAiApprovalAuthority { 1000L }
        val session = authority.openSession()
        val proposal = authority.proposalPort()
            .propose(session, "SCAN_LOCAL", "scope=local")

        authority.decisionPort().reject(proposal)

        assertFalse(authority.decisionPort().approve(proposal))
    }

    @Test
    fun approvalIsSingleUse() {
        val authority = SentinelAiApprovalAuthority { 1000L }
        val session = authority.openSession()
        val proposal = authority.proposalPort()
            .propose(session, "SCAN_LOCAL", "scope=local")

        assertTrue(authority.decisionPort().approve(proposal))
        assertFalse(authority.decisionPort().approve(proposal))
    }

    @Test
    fun globalRevocationBlocksApproval() {
        val authority = SentinelAiApprovalAuthority { 1000L }
        val session = authority.openSession()
        val proposal = authority.proposalPort()
            .propose(session, "SCAN_LOCAL", "scope=local")

        authority.decisionPort().revokeAll()

        assertFalse(authority.decisionPort().approve(proposal))
    }
}
