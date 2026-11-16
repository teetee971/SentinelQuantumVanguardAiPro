package com.sentinel.quantum.ai

/** Process-local trust root. Engine receives ONLY ProposalPort.
 * approve checks a trusted authentication verifier bound to the exact displayed proposal.
 * This verifier must validate a completed Android authentication, not start an async prompt.
 * No verifier or registered action means no approval/execution. A boolean returned by a
 * standalone controller is never accepted as execution authority.
 *
 * execute and revocation share one lock. Effective revocation is when revoke returns:
 * it waits for an already-running synchronous effect; accepted Android effects cannot be undone.
 */
class SentinelAiApprovalAuthority(
    clock: () -> Long,
    actions: Map<String, SentinelAiActionPolicy> = emptyMap(),
    private val authenticated: (Proposal) -> Boolean = { false }
) {
    data class Proposal(
        val token: String,
        val sessionId: String,
        val actionId: String,
        val parameters: String,
        val expiresAtMs: Long
    )

    interface ProposalPort {
        fun propose(sessionId: String, actionId: String, parameters: String): Proposal
    }

    enum class ExecutionResult { EXECUTED, REJECTED, OUTCOME_UNKNOWN }

    interface DecisionPort {
        fun approve(proposal: Proposal): Boolean
        fun execute(proposal: Proposal): ExecutionResult
        fun reject(proposal: Proposal)
        fun revokeAll()
    }

    private data class Entry(val proposal: Proposal, var approved: Boolean = false)
    private val lock = Any()
    private val controller = SentinelAiSecureController(clock)
    private val catalog = actions.toMap()
    private val entries = mutableMapOf<String, Entry>()
    private var generation = 0L
    private var insideCheck = false

    private fun <T> checked(block: () -> T): T {
        check(!insideCheck) { "Reentrant authorization callback" }
        insideCheck = true
        return try { block() } finally { insideCheck = false }
    }
    private val checking = mutableMapOf<String, Proposal>()

    private fun now(): Long = try { controller.nowMs() } catch (error: Exception) {
        entries.clear()
        throw error
    }

    private fun prune(at: Long) {
        entries.entries.removeAll { at >= it.value.proposal.expiresAtMs }
    }

    private fun matches(proposal: Proposal): Entry? =
        entries[proposal.token]?.takeIf { it.proposal == proposal }

    private val proposals = object : ProposalPort {
        override fun propose(sessionId: String, actionId: String, parameters: String): Proposal = synchronized(lock) {
            check(!insideCheck) { "Reentrant proposal" }
            require(SentinelAiInputBounds.valid(actionId, parameters))
            val policy = catalog[actionId] ?: throw IllegalArgumentException("Action not registered")
            val version = generation
            check(checked { policy.validate(parameters) }) { "Invalid action parameters" }
            check(version == generation) { "Authorization changed" }
            prune(now())
            check(entries.size < SentinelAiSecureController.MAX_PENDING) { "Too many proposals" }
            val approval = controller.request(sessionId, actionId, parameters)
            val proposal = Proposal(approval.token, sessionId, actionId, parameters, approval.expiresAtMs)
            entries[proposal.token] = Entry(proposal)
            proposal
        }
    }

    private val decisions = object : DecisionPort {
        override fun approve(proposal: Proposal): Boolean = synchronized(lock) {
            if (insideCheck) return@synchronized false
            val entry = matches(proposal) ?: return@synchronized false
            if (entry.approved) return@synchronized false
            val version = generation
            // Remove the proposal BEFORE calling foreign verifier code to prevent reentrant approval.
            entries.remove(proposal.token)
            checking[proposal.token] = proposal
            val valid = try { checked { authenticated(proposal) } } catch (_: Exception) { false }
                finally { checking.remove(proposal.token) }
            if (!valid || version != generation) {
                controller.reject(proposal.token, proposal.sessionId)
                return@synchronized false
            }
            val confirmed = try {
                controller.confirm(proposal.token, proposal.sessionId, proposal.actionId, proposal.parameters)
            } catch (_: Exception) { entries.clear(); false }
            if (confirmed) {
                entry.approved = true
                entries[proposal.token] = entry
            }
            confirmed
        }

        override fun execute(proposal: Proposal): ExecutionResult = synchronized(lock) {
            if (insideCheck) return@synchronized ExecutionResult.REJECTED
            val entry = matches(proposal) ?: return@synchronized ExecutionResult.REJECTED
            // Every attempt consumes the capability, including policy/permission failures.
            entries.remove(proposal.token)
            if (!entry.approved) {
                controller.reject(proposal.token, proposal.sessionId)
                return@synchronized ExecutionResult.REJECTED
            }
            val version = generation
            val policy = catalog[proposal.actionId] ?: return@synchronized ExecutionResult.REJECTED
            checking[proposal.token] = proposal
            val allowed = try {
                checked { policy.validate(proposal.parameters) && policy.permitted() } &&
                    now() < proposal.expiresAtMs && version == generation &&
                    controller.isSessionActive(proposal.sessionId)
            } catch (_: Exception) { false }
                finally { checking.remove(proposal.token) }
            if (!allowed) return@synchronized ExecutionResult.REJECTED
            try {
                policy.execute(proposal.parameters)
                ExecutionResult.EXECUTED
            } catch (_: Exception) {
                // Android may already have accepted a side effect. Never invent success or retry.
                ExecutionResult.OUTCOME_UNKNOWN
            }
        }

        override fun reject(proposal: Proposal) = synchronized(lock) {
            if (matches(proposal) != null || checking[proposal.token] == proposal) {
                generation++
                entries.remove(proposal.token)
                controller.reject(proposal.token, proposal.sessionId)
            }
        }

        override fun revokeAll() = this@SentinelAiApprovalAuthority.revokeAll()
    }

    fun openSession(): String = synchronized(lock) { controller.openSession() }
    fun proposalPort(): ProposalPort = proposals
    fun decisionPort(): DecisionPort = decisions

    fun revokeSession(sessionId: String) = synchronized(lock) {
        generation++
        controller.revokeSession(sessionId)
        entries.entries.removeAll { it.value.proposal.sessionId == sessionId }
    }

    fun revokeAll() = synchronized(lock) {
        generation++
        controller.revokeAll()
        entries.clear()
    }
}
