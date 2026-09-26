package com.sentinel.quantum.wearable.security

/**
 * Opaque evidence that a wearable handshake transcript has passed a verifier owned by this
 * security module. Transport/adaptor modules can consume this type but cannot construct it.
 */
class VerifiedWearableHandshake internal constructor(
    val stableId: String,
    val keyFingerprintSha256: String,
    val sessionId: String,
    val protocolVersion: Int,
    val capabilities: Set<String>
) {
    init {
        require(stableId.isNotBlank())
        require(keyFingerprintSha256.matches(Regex("^[0-9a-f]{64}$")))
        require(sessionId.isNotBlank())
        require(protocolVersion > 0)
        require(capabilities.isNotEmpty())
        require(capabilities.none { it.isBlank() })
    }
}

/**
 * Security-owned issuance boundary. A concrete cryptographic verifier will call this only after
 * signature/key/session validation; platform transport observations are deliberately insufficient.
 */
interface WearableHandshakeVerifier {
    fun verify(candidate: WearableHandshakeCandidate): VerifiedWearableHandshake?
}

data class WearableHandshakeCandidate(
    val stableId: String,
    val keyFingerprintSha256: String,
    val sessionId: String,
    val protocolVersion: Int,
    val capabilities: Set<String>,
    val challengeNonce: String,
    val issuedAtMs: Long,
    val signedTranscript: ByteArray,
    val signature: ByteArray
) {
    init {
        require(stableId.isNotBlank())
        require(keyFingerprintSha256.matches(Regex("^[0-9a-f]{64}$")))
        require(sessionId.isNotBlank())
        require(protocolVersion > 0)
        require(capabilities.isNotEmpty())
        require(capabilities.none { it.isBlank() })
        require(challengeNonce.matches(Regex("^[A-Za-z0-9_-]{22,128}$")))
        require(issuedAtMs >= 0)
        require(signedTranscript.isNotEmpty())
        require(signature.isNotEmpty())
    }

    override fun equals(other: Any?): Boolean =
        other is WearableHandshakeCandidate &&
            stableId == other.stableId &&
            keyFingerprintSha256 == other.keyFingerprintSha256 &&
            sessionId == other.sessionId &&
            protocolVersion == other.protocolVersion &&
            capabilities == other.capabilities &&
            challengeNonce == other.challengeNonce &&
            issuedAtMs == other.issuedAtMs &&
            signedTranscript.contentEquals(other.signedTranscript) &&
            signature.contentEquals(other.signature)

    override fun hashCode(): Int {
        var result = stableId.hashCode()
        result = 31 * result + keyFingerprintSha256.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + protocolVersion
        result = 31 * result + capabilities.hashCode()
        result = 31 * result + challengeNonce.hashCode()
        result = 31 * result + issuedAtMs.hashCode()
        result = 31 * result + signedTranscript.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
