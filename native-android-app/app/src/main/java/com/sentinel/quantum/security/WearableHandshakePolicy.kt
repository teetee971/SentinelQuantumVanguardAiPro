package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.security.VerifiedWearableHandshake

/**
 * Fail-closed domain policy for a future authenticated wearable handshake.
 *
 * The crypto implementation is deliberately separate. Transport adapters must not manufacture
 * cryptographic truth: only the crypto boundary can issue [VerifiedHandshakeProof].
 */
data class PairingConfirmation internal constructor(
    val stableId: String,
    val sessionId: String,
    val confirmedAtMs: Long
) {
    init {
        require(stableId.isNotBlank())
        require(sessionId.isNotBlank())
        require(confirmedAtMs >= 0)
    }
}

enum class WearableHandshakeDecision {
    ACCEPT,
    EXPECTED_IDENTITY_NOT_BOUND,
    IDENTITY_MISMATCH,
    KEY_MISMATCH,
    SESSION_MISMATCH,
    USER_CONFIRMATION_REQUIRED,
    USER_CONFIRMATION_MISMATCH
}

object WearableHandshakePolicy {
    fun evaluate(
        expected: WearableIdentityProof,
        session: WearableSession,
        verifiedProof: VerifiedWearableHandshake?,
        pairingConfirmation: PairingConfirmation?
    ): WearableHandshakeDecision {
        val expectedKey = expected.keyFingerprintSha256
            ?: return WearableHandshakeDecision.EXPECTED_IDENTITY_NOT_BOUND
        val proof = verifiedProof ?: return WearableHandshakeDecision.IDENTITY_MISMATCH

        if (proof.stableId != expected.stableId) return WearableHandshakeDecision.IDENTITY_MISMATCH
        if (proof.keyFingerprintSha256 != expectedKey) return WearableHandshakeDecision.KEY_MISMATCH
        if (proof.sessionId != session.sessionId ||
            proof.protocolVersion != session.protocolVersion ||
            proof.capabilities != session.negotiatedCapabilities.map { it.name }.toSet()
        ) return WearableHandshakeDecision.SESSION_MISMATCH

        val confirmation = pairingConfirmation
            ?: return WearableHandshakeDecision.USER_CONFIRMATION_REQUIRED
        if (confirmation.stableId != proof.stableId ||
            confirmation.sessionId != proof.sessionId
        ) return WearableHandshakeDecision.USER_CONFIRMATION_MISMATCH

        return WearableHandshakeDecision.ACCEPT
    }
}
