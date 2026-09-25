package com.sentinel.quantum.security

/**
 * Fail-closed domain policy for a future authenticated wearable handshake.
 *
 * The crypto implementation is deliberately separate. Transport adapters must not manufacture
 * cryptographic truth: only the crypto boundary can issue [VerifiedHandshakeProof].
 */
data class VerifiedHandshakeTranscript internal constructor(
    val stableId: String,
    val keyFingerprintSha256: String,
    val sessionId: String,
    val protocolVersion: Int,
    val capabilities: Set<WearableCapability>
) {
    init {
        require(stableId.isNotBlank())
        require(keyFingerprintSha256.matches(Regex("^[0-9a-f]{64}$")))
        require(sessionId.isNotBlank())
        require(protocolVersion > 0)
        require(capabilities.isNotEmpty())
    }
}

class VerifiedHandshakeProof internal constructor(
    internal val transcript: VerifiedHandshakeTranscript
)

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
        verifiedProof: VerifiedHandshakeProof?,
        pairingConfirmation: PairingConfirmation?
    ): WearableHandshakeDecision {
        val expectedKey = expected.keyFingerprintSha256
            ?: return WearableHandshakeDecision.EXPECTED_IDENTITY_NOT_BOUND
        val transcript = verifiedProof?.transcript
            ?: return WearableHandshakeDecision.IDENTITY_MISMATCH

        if (transcript.stableId != expected.stableId) return WearableHandshakeDecision.IDENTITY_MISMATCH
        if (transcript.keyFingerprintSha256 != expectedKey) return WearableHandshakeDecision.KEY_MISMATCH
        if (transcript.sessionId != session.sessionId ||
            transcript.protocolVersion != session.protocolVersion ||
            transcript.capabilities != session.negotiatedCapabilities
        ) return WearableHandshakeDecision.SESSION_MISMATCH

        val confirmation = pairingConfirmation
            ?: return WearableHandshakeDecision.USER_CONFIRMATION_REQUIRED
        if (confirmation.stableId != transcript.stableId ||
            confirmation.sessionId != transcript.sessionId
        ) return WearableHandshakeDecision.USER_CONFIRMATION_MISMATCH

        return WearableHandshakeDecision.ACCEPT
    }
}
