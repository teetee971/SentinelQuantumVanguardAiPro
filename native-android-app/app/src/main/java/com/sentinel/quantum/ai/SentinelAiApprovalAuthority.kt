package com.sentinel.quantum.ai

/**
 * Process-local approval authority.
 *
 * The proposal port cannot approve or execute actions.
 * The decision port must be held only by trusted Android UI code.
 *
 * This class does not authenticate the human operator.
 */
class SentinelAiApprovalAuthority(
    clock: () -> Long
) {
    data class Proposal(
        val token: String,
        val sessionId: String,
        val actionId: String,
        val parameters: String,
        val expiresAtMs: Long
    )

    interface ProposalPort {
        fun propose(
            sessionId: String,
            actionId: String,
            parameters: String
        ): Proposal
    }

    interface DecisionPort {
        fun approve(proposal: Proposal): Boolean
        fun reject(proposal: Proposal)
        fun revokeAll()
    }

    private val controller = SentinelAiSecureController(clock)

    private val proposals = object : ProposalPort {
        override fun propose(
            sessionId: String,
            actionId: String,
            parameters: String
        ): Proposal {
            val approval = controller.request(
                sessionId,
                actionId,
                parameters
            )
            return Proposal(
                approval.token,
                approval.sessionId,
                approval.actionId,
                approval.parameters,
                approval.expiresAtMs
            )
        }
    }

    private val decisions = object : DecisionPort {
        override fun approve(proposal: Proposal): Boolean =
            controller.confirm(
                proposal.token,
                proposal.sessionId,
                proposal.actionId,
                proposal.parameters
            )

        override fun reject(proposal: Proposal) {
            controller.reject(
                token = proposal.token,
                sessionId = proposal.sessionId
            )
        }

        override fun revokeAll() {
            controller.revokeAll()
        }
    }

    fun openSession(): String = controller.openSession()

    fun proposalPort(): ProposalPort = proposals

    fun decisionPort(): DecisionPort = decisions

    fun revokeSession(sessionId: String) {
        controller.revokeSession(sessionId)
    }
}
