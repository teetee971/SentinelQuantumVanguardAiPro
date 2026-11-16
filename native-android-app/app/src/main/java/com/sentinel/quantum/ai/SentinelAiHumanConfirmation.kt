package com.sentinel.quantum.ai

/** One-use binding for the trusted Android credential-result adapter. This class does NOT
 * authenticate humans: complete may be called ONLY after a genuine system credential result.
 * Never give this object or a decision port to the model. No proof survives process death.
 */
internal class SentinelAiHumanConfirmation(private val clock: () -> Long) {
    class Ticket internal constructor(val proposal: SentinelAiApprovalAuthority.Proposal, val expiresAt: Long)
    private var waiting: Ticket? = null
    private var proof: Ticket? = null
    private var last = -1L
    private var poisoned = false
    private fun now(): Long? {
        if (poisoned) return null
        val current = try { clock() } catch (_: Exception) { -1L }
        if (current < 0 || current < last) {
            poisoned = true; waiting = null; proof = null; return null
        }
        last = current
        return current
    }
    @Synchronized fun begin(proposal: SentinelAiApprovalAuthority.Proposal): Ticket {
        waiting = null; proof = null
        val at = checkNotNull(now()) { "Authentication clock unavailable" }
        check(at < proposal.expiresAtMs && at <= Long.MAX_VALUE - 30000) { "Proposal expired" }
        return Ticket(proposal, minOf(proposal.expiresAtMs, at + 30000)).also { waiting = it }
    }
    @Synchronized fun complete(ticket: Ticket, systemAuthenticated: Boolean): Boolean {
        val at = now() ?: return false
        if (waiting !== ticket) return false
        waiting = null; proof = null
        if (!systemAuthenticated || at >= ticket.expiresAt) return false
        proof = ticket
        return true
    }
    @Synchronized fun consume(proposal: SentinelAiApprovalAuthority.Proposal): Boolean {
        val ready = proof
        proof = null
        val at = now() ?: return false
        return ready != null && ready.proposal == proposal && at < ready.expiresAt
    }
    @Synchronized fun revoke() { waiting = null; proof = null }
}
