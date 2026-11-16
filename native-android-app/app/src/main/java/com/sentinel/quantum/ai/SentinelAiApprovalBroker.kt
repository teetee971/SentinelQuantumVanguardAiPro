package com.sentinel.quantum.ai

/**
 * AI-facing authorization boundary.
 *
 * The AI can request an approval but cannot confirm it.
 * This class never executes Android actions.
 */
class SentinelAiApprovalBroker(
    private val controller: SentinelAiSecureController
) {
    data class Proposal(
        val token: String,
        val sessionId: String,
        val actionId: String,
        val parameters: String,
        val expiresAtMs: Long
    )

    fun propose(
        sessionId: String,
        actionId: String,
        parameters: String
    ): Proposal {
        val approval = controller.request(
            sessionId = sessionId,
            actionId = actionId,
            parameters = parameters
        )

        return Proposal(
            token = approval.token,
            sessionId = approval.sessionId,
            actionId = approval.actionId,
            parameters = approval.parameters,
            expiresAtMs = approval.expiresAtMs
        )
    }
}
