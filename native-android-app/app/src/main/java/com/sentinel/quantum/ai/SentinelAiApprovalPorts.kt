package com.sentinel.quantum.ai

/**
 * Constructs approval capabilities at a trusted composition root.
 *
 * Never pass [Owner] or [DecisionPort] to an AI engine.
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

        fun revokeSession(sessionId: String) {
            authority.revokeSession(sessionId)
        }

        fun revokeAll() {
            decisions.revokeAll()
        }

        companion object {
            internal fun create(clock: () -> Long): Owner =
                Owner(SentinelAiApprovalAuthority(clock))
        }
    }

    /**
     * Must be called only from trusted Android application setup.
     */
    fun createOwner(clock: () -> Long): Owner =
        Owner.create(clock)
}
