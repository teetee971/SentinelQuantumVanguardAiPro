package com.sentinel.quantum.wearable.security

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Ed25519 verifier for Sentinel wearable handshakes.
 *
 * The signed bytes must equal the canonical transcript derived from the candidate fields.
 * The public key fingerprint is independently recomputed and must match the bound fingerprint.
 * Replay protection is intentionally a separate stateful boundary; this class only establishes
 * cryptographic authenticity and transcript integrity.
 */
class Ed25519WearableHandshakeVerifier(
    publicKeyX509: ByteArray
) : WearableHandshakeVerifier {
    private val publicKey: PublicKey = KeyFactory.getInstance("Ed25519")
        .generatePublic(X509EncodedKeySpec(publicKeyX509.copyOf()))
    private val fingerprint = sha256Hex(publicKey.encoded)

    override fun verify(candidate: WearableHandshakeCandidate): VerifiedWearableHandshake? {
        if (!MessageDigest.isEqual(
                fingerprint.toByteArray(StandardCharsets.US_ASCII),
                candidate.keyFingerprintSha256.toByteArray(StandardCharsets.US_ASCII)
            )
        ) return null

        val canonical = WearableHandshakeTranscriptCodec.encode(
            stableId = candidate.stableId,
            keyFingerprintSha256 = candidate.keyFingerprintSha256,
            sessionId = candidate.sessionId,
            protocolVersion = candidate.protocolVersion,
            capabilities = candidate.capabilities,
            challengeNonce = candidate.challengeNonce,
            issuedAtMs = candidate.issuedAtMs
        )
        if (!MessageDigest.isEqual(canonical, candidate.signedTranscript)) return null

        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(canonical)
        if (!verifier.verify(candidate.signature)) return null

        return VerifiedWearableHandshake(
            stableId = candidate.stableId,
            keyFingerprintSha256 = candidate.keyFingerprintSha256,
            sessionId = candidate.sessionId,
            protocolVersion = candidate.protocolVersion,
            capabilities = candidate.capabilities.toSet()
        )
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

object WearableHandshakeTranscriptCodec {
    private const val DOMAIN = "sentinel-wearable-handshake-v1"

    fun encode(
        stableId: String,
        keyFingerprintSha256: String,
        sessionId: String,
        protocolVersion: Int,
        capabilities: Set<String>,
        challengeNonce: String,
        issuedAtMs: Long
    ): ByteArray {
        require(stableId.isNotBlank())
        require(keyFingerprintSha256.matches(Regex("^[0-9a-f]{64}$")))
        require(sessionId.isNotBlank())
        require(protocolVersion > 0)
        require(capabilities.isNotEmpty())
        require(capabilities.none { it.isBlank() || '\n' in it || '\r' in it })
        require('\n' !in stableId && '\r' !in stableId)
        require('\n' !in sessionId && '\r' !in sessionId)

        val sortedCapabilities = capabilities.toSortedSet().joinToString(",")
        return buildString {
            append(DOMAIN).append('\n')
            append(stableId).append('\n')
            append(keyFingerprintSha256).append('\n')
            append(sessionId).append('\n')
            append(protocolVersion).append('\n')
            append(sortedCapabilities).append('\n')
        }.toByteArray(StandardCharsets.UTF_8)
    }
}
