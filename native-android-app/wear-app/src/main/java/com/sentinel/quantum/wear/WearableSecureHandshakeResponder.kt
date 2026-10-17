package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.security.WearableHandshakeTranscriptCodec
import com.sentinel.quantum.wearable.security.WearableHpkeKeyPair
import java.security.GeneralSecurityException
import java.security.MessageDigest

interface WearableHpkeRecipientSession {
    val publicKeyEncoded: ByteArray
    val fingerprintSha256: String

    @Throws(GeneralSecurityException::class)
    fun decrypt(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray
}

interface WearableHpkeSessionFactory {
    @Throws(GeneralSecurityException::class)
    fun create(): WearableHpkeRecipientSession
}

internal class TinkWearableHpkeSessionFactory : WearableHpkeSessionFactory {
    override fun create(): WearableHpkeRecipientSession {
        val pair = WearableHpkeKeyPair.generate()
        return object : WearableHpkeRecipientSession {
            override val publicKeyEncoded: ByteArray
                get() = pair.publicKey.encoded()
            override val fingerprintSha256: String
                get() = pair.publicKey.fingerprintSha256
            override fun decrypt(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray =
                pair.decrypt(ciphertext, contextInfo)
        }
    }
}

data class WearableHandshakeRequest(
    val stableId: String,
    val sessionId: String,
    val capabilities: Set<String>,
    val challengeNonce: String,
    val issuedAtMs: Long,
    val phoneHpkePublicKeyFingerprintSha256: String
) {
    init {
        require(stableId.isNotBlank())
        require(sessionId.isNotBlank())
        require(capabilities.isNotEmpty())
        require(capabilities.none { it.isBlank() })
        require(challengeNonce.matches(Regex("^[A-Za-z0-9_-]{22,128}$")))
        require(issuedAtMs >= 0)
        require(phoneHpkePublicKeyFingerprintSha256.matches(Regex("^[0-9a-f]{64}$")))
    }
}

data class WearableHandshakeResponse(
    val identityPublicKeyX509: ByteArray,
    val identityKeyFingerprintSha256: String,
    val wearableHpkePublicKeyEncoded: ByteArray,
    val wearableHpkePublicKeyFingerprintSha256: String,
    val signedTranscript: ByteArray,
    val signature: ByteArray
) {
    init {
        require(identityPublicKeyX509.isNotEmpty())
        require(identityKeyFingerprintSha256.matches(Regex("^[0-9a-f]{64}$")))
        require(wearableHpkePublicKeyEncoded.isNotEmpty())
        require(wearableHpkePublicKeyFingerprintSha256.matches(Regex("^[0-9a-f]{64}$")))
        require(signedTranscript.isNotEmpty())
        require(signature.isNotEmpty())
    }
}

data class WearableHandshakeResponderResult(
    val response: WearableHandshakeResponse,
    val session: WearableHpkeRecipientSession
)

class WearableSecureHandshakeResponder(
    private val identityStore: WearableSecureIdentityStore,
    private val hpkeSessionFactory: WearableHpkeSessionFactory = TinkWearableHpkeSessionFactory()
) {
    @Throws(GeneralSecurityException::class)
    fun respond(request: WearableHandshakeRequest): WearableHandshakeResponderResult {
        val identity = identityStore.snapshot()
        val session = hpkeSessionFactory.create()
        val publicKeyEncoded = session.publicKeyEncoded.copyOf()
        require(publicKeyEncoded.isNotEmpty() && publicKeyEncoded.size <= MAX_HPKE_PUBLIC_KEY_BYTES) {
            "wearable HPKE public key is invalid"
        }
        val expectedHpkeFingerprint = MessageDigest.getInstance("SHA-256")
            .digest(publicKeyEncoded)
            .joinToString("") { "%02x".format(it) }
        require(
            MessageDigest.isEqual(
                expectedHpkeFingerprint.toByteArray(Charsets.US_ASCII),
                session.fingerprintSha256.toByteArray(Charsets.US_ASCII)
            )
        ) { "wearable HPKE public key fingerprint mismatch" }

        val transcript = WearableHandshakeTranscriptCodec.encode(
            stableId = request.stableId,
            keyFingerprintSha256 = identity.fingerprintSha256,
            sessionId = request.sessionId,
            protocolVersion = SECURE_PROTOCOL_VERSION,
            capabilities = request.capabilities.toSet(),
            challengeNonce = request.challengeNonce,
            issuedAtMs = request.issuedAtMs,
            phoneHpkePublicKeyFingerprintSha256 = request.phoneHpkePublicKeyFingerprintSha256,
            wearableHpkePublicKeyFingerprintSha256 = session.fingerprintSha256
        )
        val signature = identityStore.sign(transcript)
        return WearableHandshakeResponderResult(
            response = WearableHandshakeResponse(
                identityPublicKeyX509 = identity.publicKeyX509.copyOf(),
                identityKeyFingerprintSha256 = identity.fingerprintSha256,
                wearableHpkePublicKeyEncoded = publicKeyEncoded,
                wearableHpkePublicKeyFingerprintSha256 = session.fingerprintSha256,
                signedTranscript = transcript.copyOf(),
                signature = signature.copyOf()
            ),
            session = session
        )
    }

    companion object {
        const val SECURE_PROTOCOL_VERSION = 2
        private const val MAX_HPKE_PUBLIC_KEY_BYTES = 4 * 1024
    }
}
