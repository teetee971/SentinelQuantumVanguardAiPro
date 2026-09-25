package com.sentinel.quantum.security

/**
 * Fail-closed policy for a future authenticated wearable handshake.
 *
 * This class does not implement cryptography. Transport adapters must supply independently
 * verified signature/key-agreement facts. No Bluetooth bond, companion association, display
 * name or radio address can satisfy these requirements.
 */
data class WearableHandshakeEvidence(
    val stableIdMatches: Boolean,
    val expectedKeyFingerprintMatches: Boolean,
    val peerSignatureVerified: Boolean,
    val ephemeralKeyAgreementCompleted: Boolean,
    val transcriptBoundToSession: Boolean,
    val userConfirmedPairing: Boolean,
    val negotiatedProtocolVersion: Int?,
    val negotiatedCapabilities: Set<WearableCapability>
)

enum class WearableHandshakeDecision {
    ACCEPT,
    IDENTITY_MISMATCH,
    KEY_MISMATCH,
    SIGNATURE_NOT_VERIFIED,
    KEY_AGREEMENT_INCOMPLETE,
    SESSION_TRANSCRIPT_NOT_BOUND,
    USER_CONFIRMATION_REQUIRED,
    PROTOCOL_NOT_NEGOTIATED,
    NO_CAPABILITIES_NEGOTIATED
}

object WearableHandshakePolicy {
    fun evaluate(evidence: WearableHandshakeEvidence): WearableHandshakeDecision = when {
        !evidence.stableIdMatches -> WearableHandshakeDecision.IDENTITY_MISMATCH
        !evidence.expectedKeyFingerprintMatches -> WearableHandshakeDecision.KEY_MISMATCH
        !evidence.peerSignatureVerified -> WearableHandshakeDecision.SIGNATURE_NOT_VERIFIED
        !evidence.ephemeralKeyAgreementCompleted -> WearableHandshakeDecision.KEY_AGREEMENT_INCOMPLETE
        !evidence.transcriptBoundToSession -> WearableHandshakeDecision.SESSION_TRANSCRIPT_NOT_BOUND
        !evidence.userConfirmedPairing -> WearableHandshakeDecision.USER_CONFIRMATION_REQUIRED
        evidence.negotiatedProtocolVersion == null || evidence.negotiatedProtocolVersion <= 0 ->
            WearableHandshakeDecision.PROTOCOL_NOT_NEGOTIATED
        evidence.negotiatedCapabilities.isEmpty() -> WearableHandshakeDecision.NO_CAPABILITIES_NEGOTIATED
        else -> WearableHandshakeDecision.ACCEPT
    }
}
