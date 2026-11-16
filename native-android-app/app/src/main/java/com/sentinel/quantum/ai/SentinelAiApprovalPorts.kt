package com.sentinel.quantum.ai

/**
 * Constructs approval capabilities at a trusted composition root.
 *
 * Never pass [Owner] or [SentinelAiApprovalAuthority.DecisionPort] to an AI engine.
 *
 * This is a capability boundary, not human authentication.
 */
object SentinelAiApprovalPorts {

    class Owner private constructor(
        private val authority: SentinelAiApprovalAuthority
    ) {
        val proposals: SentinelAiApprovalAuthority.ProposalPort =
            authority.proposalPort()

        val decisions: SentinelAiApprovalAuthority.DecisionPort =
            authority.decisionPort()

        fun openSession(): String = authority.openSession()

        /** Bind to an Android ViewModel/lifecycle; close on disposal/logout/background policy. */
        fun openSessionScope(): Session = Session(authority, authority.openSession())

        fun revokeSession(sessionId: String) {
            authority.revokeSession(sessionId)
        }

        fun revokeAll() {
            decisions.revokeAll()
        }

        companion object {
            internal fun create(
                clock: () -> Long,
                actions: Map<String, SentinelAiActionPolicy>,
                authenticated: (SentinelAiApprovalAuthority.Proposal) -> Boolean
            ): Owner = Owner(SentinelAiApprovalAuthority(clock, actions, authenticated))
        }
    }

    /** Engine-facing lifecycle handle: no approval, execution, owner or session-id accessor. */
    class Session internal constructor(
        private val authority: SentinelAiApprovalAuthority,
        private val sessionId: String
    ) : AutoCloseable {
        private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

        fun propose(actionId: String, parameters: String): SentinelAiApprovalAuthority.Proposal {
            check(!closed.get()) { "Session closed" }
            return authority.proposalPort().propose(sessionId, actionId, parameters)
        }

        override fun close() {
            closed.set(true)
            // Every close waits for revocation; no nested Session/Authority monitor ordering.
            authority.revokeSession(sessionId)
        }
    }

    /**
     * Must be called only from trusted Android application setup.
     */
    fun createOwner(clock: () -> Long): Owner =
        Owner.create(clock, emptyMap(), { false })

    fun createOwner(
        clock: () -> Long,
        actions: Map<String, SentinelAiActionPolicy>,
        authenticated: (SentinelAiApprovalAuthority.Proposal) -> Boolean
    ): Owner = Owner.create(clock, actions, authenticated)
}
