package com.sentinel.quantum.wearable.security

import java.nio.charset.StandardCharsets
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
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

    override fun verify(candidate: WearableHandshakeCandidate): VerifiedWearableHandshake? =
        runCatching { verifySnapshot(candidate) }.getOrNull()

    private fun verifySnapshot(candidate: WearableHandshakeCandidate): VerifiedWearableHandshake? {
        // The proof must contain exactly the capabilities whose transcript was verified,
        // even if a transport supplied a mutable set and changes it during verification.
        val capabilities = candidate.capabilities.toSet()
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
            capabilities = capabilities,
            challengeNonce = candidate.challengeNonce,
            issuedAtMs = candidate.issuedAtMs,
            phoneHpkePublicKeyFingerprintSha256 = candidate.phoneHpkePublicKeyFingerprintSha256,
            wearableHpkePublicKeyFingerprintSha256 = candidate.wearableHpkePublicKeyFingerprintSha256
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
            capabilities = capabilities,
            phoneHpkePublicKeyFingerprintSha256 = candidate.phoneHpkePublicKeyFingerprintSha256,
            wearableHpkePublicKeyFingerprintSha256 = candidate.wearableHpkePublicKeyFingerprintSha256
        )
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

object WearableHandshakeTranscriptCodec {
    private const val DOMAIN_V1 = "sentinel-wearable-handshake-v1"
    private const val DOMAIN_V2 = "sentinel-wearable-handshake-v2"
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

    fun encode(
        stableId: String,
        keyFingerprintSha256: String,
        sessionId: String,
        protocolVersion: Int,
        capabilities: Set<String>,
        challengeNonce: String,
        issuedAtMs: Long,
        phoneHpkePublicKeyFingerprintSha256: String? = null,
        wearableHpkePublicKeyFingerprintSha256: String? = null
    ): ByteArray {
        require(stableId.isNotBlank())
        require(keyFingerprintSha256.matches(SHA256_HEX))
        require(sessionId.isNotBlank())
        require(capabilities.isNotEmpty())
        // Commas delimit capabilities. Reject embedded delimiters so {"A,B"} cannot authenticate
        // as {"A", "B"} with the same signed bytes.
        require(capabilities.none { it.isBlank() || ',' in it || '\n' in it || '\r' in it })
        require('\n' !in stableId && '\r' !in stableId)
        require('\n' !in sessionId && '\r' !in sessionId)
        require(challengeNonce.matches(Regex("^[A-Za-z0-9_-]{22,128}$")))
        require(issuedAtMs >= 0)

        val domain = when (protocolVersion) {
            1 -> {
                require(
                    phoneHpkePublicKeyFingerprintSha256 == null &&
                        wearableHpkePublicKeyFingerprintSha256 == null
                ) { "protocol v1 cannot carry secure HPKE bindings" }
                DOMAIN_V1
            }
            2 -> {
                require(phoneHpkePublicKeyFingerprintSha256?.matches(SHA256_HEX) == true) {
                    "protocol v2 requires a lowercase SHA-256 phone HPKE fingerprint"
                }
                require(wearableHpkePublicKeyFingerprintSha256?.matches(SHA256_HEX) == true) {
                    "protocol v2 requires a lowercase SHA-256 wearable HPKE fingerprint"
                }
                DOMAIN_V2
            }
            else -> throw IllegalArgumentException("unsupported wearable handshake protocol version")
        }

        val sortedCapabilities = capabilities.toSortedSet().joinToString(",")
        val transcript = buildString {
            append(domain).append('\n')
            append(stableId).append('\n')
            append(keyFingerprintSha256).append('\n')
            append(sessionId).append('\n')
            append(protocolVersion).append('\n')
            append(sortedCapabilities).append('\n')
            append(challengeNonce).append('\n')
            append(issuedAtMs).append('\n')
            if (protocolVersion == 2) {
                append(phoneHpkePublicKeyFingerprintSha256).append('\n')
                append(wearableHpkePublicKeyFingerprintSha256).append('\n')
            }
        }
        // String.toByteArray replaces malformed UTF-16 with '?', allowing two different
        // identity/session/capability strings to share signed bytes. Refuse lossy encoding.
        val bytes = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(transcript))
        return ByteArray(bytes.remaining()).also { bytes.get(it) }
    }
}
