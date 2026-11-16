package com.sentinel.quantum.ai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class SentinelAiExecutionSecurityTest {
    private var now = 1000L
    private var permission = true
    private val effects = AtomicInteger()
    private fun action(effect: (String) -> Unit = { effects.incrementAndGet(); Unit }) =
        SentinelAiActionPolicy({ it == "scope=local" }, { permission }, effect)
    private fun authority(auth: (SentinelAiApprovalAuthority.Proposal) -> Boolean = { true }) =
        SentinelAiApprovalAuthority({ now }, mapOf("SCAN_LOCAL" to action()), auth)
    private fun proposal(a: SentinelAiApprovalAuthority, session: String = a.openSession()) =
        a.proposalPort().propose(session, "SCAN_LOCAL", "scope=local")
    private fun rejected(a: SentinelAiApprovalAuthority, p: SentinelAiApprovalAuthority.Proposal) {
        assertEquals(SentinelAiApprovalAuthority.ExecutionResult.REJECTED, a.decisionPort().execute(p))
        assertEquals(0, effects.get())
    }

    @Test fun defaultRootHasNoActions() {
        val a = SentinelAiApprovalAuthority({ now })
        assertThrows(IllegalArgumentException::class.java) { proposal(a) }
    }
    @Test fun defaultAuthenticationDeniesEvenWithCatalog() {
        val a = SentinelAiApprovalAuthority({ now }, mapOf("SCAN_LOCAL" to action()))
        val p = proposal(a)
        assertFalse(a.decisionPort().approve(p)); rejected(a, p)
    }
    @Test fun strictCatalogRejectsUnknownActionAndParameters() {
        val a = authority(); val s = a.openSession()
        assertThrows(IllegalArgumentException::class.java) { a.proposalPort().propose(s, "DELETE", "") }
        assertThrows(IllegalStateException::class.java) { a.proposalPort().propose(s, "SCAN_LOCAL", "scope=all") }
        assertEquals(0, effects.get())
    }
    @Test fun boundedParametersRejectBeforeValidator() {
        val a = authority(); val s = a.openSession()
        for (value in listOf("x".repeat(4097), "scope=local\u0000")) {
            assertThrows(IllegalArgumentException::class.java) { a.proposalPort().propose(s, "SCAN_LOCAL", value) }
        }
    }
    @Test fun unapprovedProposalCannotExecute() { val a = authority(); rejected(a, proposal(a)) }
    @Test fun successfulExecutionAndApprovalAreSingleUse() {
        val a = authority(); val p = proposal(a); val d = a.decisionPort()
        assertTrue(d.approve(p)); assertFalse(d.approve(p))
        assertEquals(SentinelAiApprovalAuthority.ExecutionResult.EXECUTED, d.execute(p))
        assertEquals(SentinelAiApprovalAuthority.ExecutionResult.REJECTED, d.execute(p))
        assertEquals(1, effects.get())
    }
    @Test fun modifiedExpiryDoesNotApproveOrExecuteOriginal() {
        val a = authority(); val p = proposal(a); val forged = p.copy(expiresAtMs = Long.MAX_VALUE)
        assertFalse(a.decisionPort().approve(forged)); rejected(a, forged)
        assertTrue(a.decisionPort().approve(p))
    }
    @Test fun authenticationFailureConsumesProposal() {
        var valid = false
        val a = authority { valid }; val p = proposal(a)
        assertFalse(a.decisionPort().approve(p)); valid = true
        assertFalse(a.decisionPort().approve(p)); rejected(a, p)
    }
    @Test fun authenticationExceptionFailsClosed() {
        val a = authority { error("Authentication unavailable") }; val p = proposal(a)
        assertFalse(a.decisionPort().approve(p)); rejected(a, p)
    }
    @Test fun reentrantGlobalRevocationDuringAuthenticationWins() {
        lateinit var a: SentinelAiApprovalAuthority
        a = authority { a.revokeAll(); true }; val p = proposal(a)
        assertFalse(a.decisionPort().approve(p)); rejected(a, p)
    }
    @Test fun reentrantApprovalCannotDuplicateCapability() {
        lateinit var a: SentinelAiApprovalAuthority
        a = authority { assertFalse(a.decisionPort().approve(it)); true }
        val p = proposal(a); assertTrue(a.decisionPort().approve(p))
        assertEquals(SentinelAiApprovalAuthority.ExecutionResult.EXECUTED, a.decisionPort().execute(p))
        assertEquals(1, effects.get())
    }
    @Test fun sessionRevocationAfterApprovalBlocksExecution() {
        val a = authority(); val p = proposal(a)
        assertTrue(a.decisionPort().approve(p)); a.revokeSession(p.sessionId); rejected(a, p)
    }
    @Test fun globalRevocationAfterApprovalBlocksExecution() {
        val a = authority(); val p = proposal(a)
        assertTrue(a.decisionPort().approve(p)); a.revokeAll(); rejected(a, p)
    }
    @Test fun expirationAfterApprovalBlocksExecutionAtExactBoundary() {
        val a = authority(); val p = proposal(a)
        assertTrue(a.decisionPort().approve(p)); now = p.expiresAtMs; rejected(a, p)
    }
    @Test fun permissionsAreRecheckedAndFailureConsumesGrant() {
        val a = authority(); val p = proposal(a)
        assertTrue(a.decisionPort().approve(p)); permission = false; rejected(a, p)
        permission = true; rejected(a, p)
    }
    @Test fun permissionExceptionNeverExecutes() {
        val policy = SentinelAiActionPolicy({ true }, { error("role lookup failed") }, { effects.incrementAndGet(); Unit })
        val a = SentinelAiApprovalAuthority({ now }, mapOf("SCAN_LOCAL" to policy), { true })
        val p = proposal(a); assertTrue(a.decisionPort().approve(p)); rejected(a, p)
    }
    @Test fun reentrantRevocationDuringPermissionCheckWins() {
        lateinit var a: SentinelAiApprovalAuthority
        val policy = SentinelAiActionPolicy({ true }, { a.revokeSession(session); true }, {})
        a = SentinelAiApprovalAuthority({ now }, mapOf("SCAN_LOCAL" to policy), { true })
        session = a.openSession(); val p = proposal(a, session)
        assertTrue(a.decisionPort().approve(p)); rejected(a, p)
    }
    private var session = ""
    @Test fun exceptionAfterEffectIsUnknownAndNeverRetried() {
        val a = SentinelAiApprovalAuthority({ now }, mapOf("SCAN_LOCAL" to action {
            effects.incrementAndGet(); error("Platform may have accepted")
        }), { true })
        val p = proposal(a); val d = a.decisionPort(); assertTrue(d.approve(p))
        assertEquals(SentinelAiApprovalAuthority.ExecutionResult.OUTCOME_UNKNOWN, d.execute(p))
        assertEquals(SentinelAiApprovalAuthority.ExecutionResult.REJECTED, d.execute(p))
        assertEquals(1, effects.get())
    }
    @Test fun rollbackAfterApprovalInvalidatesAllGrants() {
        val a = authority(); val p = proposal(a)
        assertTrue(a.decisionPort().approve(p)); now--; rejected(a, p); now = 2000
        assertThrows(IllegalStateException::class.java) { a.openSession() }
    }
    @Test fun approvedButUnusedCapabilitiesAreBounded() {
        val a = authority(); val s = a.openSession()
        repeat(SentinelAiSecureController.MAX_PENDING) { assertTrue(a.decisionPort().approve(proposal(a, s))) }
        assertThrows(IllegalStateException::class.java) { proposal(a, s) }
        now += 30_000; assertNotNull(proposal(a, s))
    }
    @Test fun concurrentExecutionIsExactlyOnce() {
        val a = authority(); val p = proposal(a); assertTrue(a.decisionPort().approve(p))
        race { a.decisionPort().execute(p) }
        assertEquals(1, effects.get())
    }
    @Test fun concurrentApprovalIsExactlyOnce() {
        val a = authority(); val p = proposal(a); val approvals = AtomicInteger()
        race { if (a.decisionPort().approve(p)) approvals.incrementAndGet() }
        assertEquals(1, approvals.get())
    }
    @Test fun confirmationRacingRevocationCannotExecuteAfterBothReturn() {
        val a = authority()
        repeat(30) {
            val p = proposal(a)
            val pool = Executors.newFixedThreadPool(2); val start = CountDownLatch(1)
            try {
                val first = pool.submit { check(start.await(5, TimeUnit.SECONDS)); a.decisionPort().approve(p) }
                val second = pool.submit { check(start.await(5, TimeUnit.SECONDS)); a.revokeSession(p.sessionId) }
                start.countDown(); first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS)
                rejected(a, p)
            } finally { start.countDown(); pool.shutdownNow() }
        }
    }
    @Test fun revocationWaitsForStartedEffectAndBlocksFutureEffects() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val revoking = CountDownLatch(1)
        val a = SentinelAiApprovalAuthority({ now }, mapOf("SCAN_LOCAL" to action {
            entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); effects.incrementAndGet(); Unit
        }), { true })
        val p = proposal(a); assertTrue(a.decisionPort().approve(p))
        val pool = Executors.newFixedThreadPool(2)
        try {
            val execution = pool.submit<SentinelAiApprovalAuthority.ExecutionResult> { a.decisionPort().execute(p) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val revocation = pool.submit { revoking.countDown(); a.revokeAll() }
            assertTrue(revoking.await(5, TimeUnit.SECONDS))
            assertThrows(TimeoutException::class.java) { revocation.get(100, TimeUnit.MILLISECONDS) }
            release.countDown()
            assertEquals(SentinelAiApprovalAuthority.ExecutionResult.EXECUTED, execution.get(5, TimeUnit.SECONDS))
            revocation.get(5, TimeUnit.SECONDS)
            assertEquals(SentinelAiApprovalAuthority.ExecutionResult.REJECTED, a.decisionPort().execute(p))
            assertEquals(1, effects.get())
        } finally { release.countDown(); pool.shutdownNow() }
    }
    @Test fun proposalCapabilityHasNoDecisionOrExecutionInterface() {
        val port = authority().proposalPort()
        assertFalse(port is SentinelAiApprovalAuthority.DecisionPort)
        assertEquals(setOf("propose"), SentinelAiApprovalAuthority.ProposalPort::class.java.declaredMethods.map { it.name }.toSet())
    }
    @Test fun clockExceptionPoisonsControllerPermanently() {
        var fail = false
        val c = SentinelAiSecureController { if (fail) error("clock failed") else now }
        val s = c.openSession(); val p = c.request(s, "SCAN_LOCAL", "scope=local")
        fail = true; assertThrows(IllegalStateException::class.java) { c.confirm(p.token, s, p.actionId, p.parameters) }
        fail = false
        assertEquals(0, c.activeSessionCount()); assertEquals(0, c.pendingCount())
        assertThrows(IllegalStateException::class.java) { c.openSession() }
    }
    @Test fun gateRollbackCannotBeRecoveredByRevocation() {
        val g = SentinelAiActionGate(); val p = g.request("SCAN_LOCAL", "scope=local", 1000)
        g.request("SCAN_LOCAL", "scope=local", 1100)
        assertThrows(IllegalStateException::class.java) { g.confirm(p.token, p.actionId, p.parameters, 1050) }
        g.revokeAll()
        assertThrows(IllegalStateException::class.java) { g.request("SCAN_LOCAL", "scope=local", 1200) }
    }
    @Test fun rejectingDuringAuthenticationCancelsApproval() {
        lateinit var a: SentinelAiApprovalAuthority
        a = authority { a.decisionPort().reject(it); true }
        val p = proposal(a); assertFalse(a.decisionPort().approve(p)); rejected(a, p)
    }
    @Test fun authenticationCannotReentrantlyFillProposalCapacity() {
        lateinit var a: SentinelAiApprovalAuthority
        a = authority {
            assertThrows(IllegalStateException::class.java) { proposal(a, it.sessionId) }
            true
        }
        val p = proposal(a); assertTrue(a.decisionPort().approve(p))
    }
    private fun race(block: () -> Unit) {
        val pool = Executors.newFixedThreadPool(8); val start = CountDownLatch(1)
        try {
            val workers = (1..8).map { pool.submit { check(start.await(5, TimeUnit.SECONDS)); block() } }
            start.countDown(); workers.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally { start.countDown(); pool.shutdownNow() }
    }
}
