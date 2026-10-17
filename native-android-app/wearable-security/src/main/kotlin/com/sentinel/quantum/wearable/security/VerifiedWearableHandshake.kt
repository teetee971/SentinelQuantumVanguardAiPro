package com.sentinel.quantum.wearable.security

private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

private fun requireProtocolKeyBindings(
    protocolVersion: Int,
    phoneHpkePublicKeyFingerprintSha256: String?,
    wearableHpkePublicKeyFingerprintSha256: String?
) {
    when (protocolVersion) {
        1 -> require(
            phoneHpkePublicKeyFingerprintSha256 == null &&
                wearableHpkePublicKeyFingerprintSha256 == null
        ) { "protocol v1 cannot carry secure HPKE bindings" }
        2 -> {
            require(phoneHpkePublicKeyFingerprintSha256?.matches(SHA256_HEX) == true) {
                "protocol v2 requires a lowercase SHA-256 phone HPKE fingerprint"
            }
            require(wearableHpkePublicKeyFingerprintSha256?.matches(SHA256_HEX) == true) {
                "protocol v2 requires a lowercase SHA-256 wearable HPKE fingerprint"
            }
        }
        else -> throw IllegalArgumentException("unsupported wearable handshake protocol version")
    }
}

/**
 * Opaque evidence that a wearable handshake transcript has passed a verifier owned by this
 * security module. Transport/adaptor modules can consume this type but cannot construct it.
 */
class VerifiedWearableHandshake internal constructor(
    val stableId: String,
    val keyFingerprintSha256: String,
    val sessionId: String,
    val protocolVersion: Int,
    capabilities: Set<String>,
    val phoneHpkePublicKeyFingerprintSha256: String? = null,
    val wearableHpkePublicKeyFingerprintSha256: String? = null
) {
    // Transport consumers must not be able to add capabilities to authenticated evidence.
    val capabilities: Set<String> = java.util.Collections.unmodifiableSet(LinkedHashSet(capabilities))

    val secureSessionBound: Boolean
        get() = protocolVersion == 2 &&
            phoneHpkePublicKeyFingerprintSha256 != null &&
            wearableHpkePublicKeyFingerprintSha256 != null

    init {
        require(stableId.isNotBlank())
        require(keyFingerprintSha256.matches(SHA256_HEX))
        require(sessionId.isNotBlank())
        require(capabilities.isNotEmpty())
        require(capabilities.none { it.isBlank() })
        requireProtocolKeyBindings(
            protocolVersion,
            phoneHpkePublicKeyFingerprintSha256,
            wearableHpkePublicKeyFingerprintSha256
        )
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
    val signature: ByteArray,
    val phoneHpkePublicKeyFingerprintSha256: String? = null,
    val wearableHpkePublicKeyFingerprintSha256: String? = null
) {
    init {
        require(stableId.isNotBlank())
        require(keyFingerprintSha256.matches(SHA256_HEX))
        require(sessionId.isNotBlank())
        require(capabilities.isNotEmpty())
        require(capabilities.none { it.isBlank() })
        require(challengeNonce.matches(Regex("^[A-Za-z0-9_-]{22,128}$")))
        require(issuedAtMs >= 0)
        require(signedTranscript.isNotEmpty())
        require(signature.isNotEmpty())
        requireProtocolKeyBindings(
            protocolVersion,
            phoneHpkePublicKeyFingerprintSha256,
            wearableHpkePublicKeyFingerprintSha256
        )
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
            signature.contentEquals(other.signature) &&
            phoneHpkePublicKeyFingerprintSha256 == other.phoneHpkePublicKeyFingerprintSha256 &&
            wearableHpkePublicKeyFingerprintSha256 == other.wearableHpkePublicKeyFingerprintSha256

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
        result = 31 * result + (phoneHpkePublicKeyFingerprintSha256?.hashCode() ?: 0)
        result = 31 * result + (wearableHpkePublicKeyFingerprintSha256?.hashCode() ?: 0)
        return result
    }
}
