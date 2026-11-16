package com.sentinel.quantum.ai

import org.junit.Assert.*
import org.junit.Test

class SentinelAiHumanConfirmationTest {
    private var now = 100L
    private fun gate() = SentinelAiHumanConfirmation { now }
    private fun proposal() = SentinelAiApprovalAuthority.Proposal("token", "session", "CREATE_AUDIT_CHECKPOINT", "", 30100)
    @Test fun completedHumanChallengeIsConsumedOnce() {
        val gate = gate();val proposal = proposal();val ticket = gate.begin(proposal)
        assertTrue(gate.complete(ticket, true)); assertTrue(gate.consume(proposal)); assertFalse(gate.consume(proposal))
    }
    @Test fun mereProposalNeverAuthenticates() { val g = gate();g.begin(proposal());assertFalse(g.consume(proposal())) }
    @Test fun cancellationNeverAuthenticates() { val g = gate();val t = g.begin(proposal());assertFalse(g.complete(t, false));assertFalse(g.consume(proposal())) }
    @Test fun wrongProposalBurnsProof() {
        val g = gate();val p = proposal();val t = g.begin(p);g.complete(t, true)
        assertFalse(g.consume(p.copy(parameters="changed")));assertFalse(g.consume(p))
    }
    @Test fun expiryBoundaryRejectsCompletion() {
        val g = gate();val t = g.begin(proposal());now = 30100
        assertFalse(g.complete(t, true));assertFalse(g.consume(proposal()))
    }
    @Test fun revocationRejectsLateSystemResult() {
        val g = gate();val t = g.begin(proposal());g.revoke();assertFalse(g.complete(t, true));assertFalse(g.consume(proposal()))
    }
    @Test fun oldResultCannotCompleteNewChallenge() {
        val g = gate();val old = g.begin(proposal());g.revoke();val current = g.begin(proposal())
        assertFalse(g.complete(old, true));assertTrue(g.complete(current, true));assertTrue(g.consume(proposal()))
    }
    @Test fun clockRollbackPermanentlyPoisonsProof() {
        val g = gate();val t = g.begin(proposal());now=99
        assertFalse(g.complete(t, true));now=200;assertFalse(g.consume(proposal()))
        assertThrows(IllegalStateException::class.java) { g.begin(proposal()) }
    }
    @Test fun concurrentConsumersHaveOneWinner() {
        val g = gate();val p = proposal();val t = g.begin(p);assertTrue(g.complete(t,true))
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try { assertEquals(1, (0..15).map { pool.submit<Boolean> { g.consume(p) } }.count { it.get() }) }
        finally { pool.shutdownNow() }
    }
}
