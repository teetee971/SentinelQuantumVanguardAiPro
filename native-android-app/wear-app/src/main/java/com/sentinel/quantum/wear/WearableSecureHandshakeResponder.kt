package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.security.WearableHandshakeTranscriptCodec
import com.sentinel.quantum.wearable.security.WearableHpkeKeyPair
import com.sentinel.quantum.wearable.security.WearableHpkePublicKey
import com.sentinel.quantum.wearable.security.WearableHpkeSender
import java.security.GeneralSecurityException
import java.security.MessageDigest

interface WearableHpkeDuplexSession {
    val wearablePublicKeyEncoded: ByteArray
    val wearableFingerprintSha256: String
    val phonePublicKeyEncoded: ByteArray
    val phoneFingerprintSha256: String

    @Throws(GeneralSecurityException::class)
    fun encryptForPhone(plaintext: ByteArray, contextInfo: ByteArray): ByteArray

    @Throws(GeneralSecurityException::class)
    fun decryptFromPhone(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray
}

interface WearableHpkeSessionFactory {
    @Throws(GeneralSecurityException::class)
    fun create(phoneHpkePublicKeyEncoded: ByteArray): WearableHpkeDuplexSession
}

internal class TinkWearableHpkeSessionFactory : WearableHpkeSessionFactory {
    override fun create(phoneHpkePublicKeyEncoded: ByteArray): WearableHpkeDuplexSession {
        val pair = WearableHpkeKeyPair.generate()
        val phonePublic = WearableHpkePublicKey.fromEncoded(phoneHpkePublicKeyEncoded)
        val sender = WearableHpkeSender.fromPublicKey(phonePublic)
        return object : WearableHpkeDuplexSession {
            override val wearablePublicKeyEncoded: ByteArray
                get() = pair.publicKey.encoded()
            override val wearableFingerprintSha256: String
                get() = pair.publicKey.fingerprintSha256
            override val phonePublicKeyEncoded: ByteArray
                get() = phonePublic.encoded()
            override val phoneFingerprintSha256: String
                get() = phonePublic.fingerprintSha256
            override fun encryptForPhone(plaintext: ByteArray, contextInfo: ByteArray): ByteArray =
                sender.encrypt(plaintext, contextInfo)
            override fun decryptFromPhone(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray =
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
    val phoneHpkePublicKeyEncoded: ByteArray,
    val phoneHpkePublicKeyFingerprintSha256: String
) {
    init {
        require(stableId.isNotBlank())
        require(sessionId.isNotBlank())
        require(capabilities.isNotEmpty())
        require(capabilities.none { it.isBlank() })
        require(challengeNonce.matches(Regex("^[A-Za-z0-9_-]{22,128}$")))
        require(issuedAtMs >= 0)
        require(phoneHpkePublicKeyEncoded.isNotEmpty() && phoneHpkePublicKeyEncoded.size <= MAX_HPKE_PUBLIC_KEY_BYTES)
        require(phoneHpkePublicKeyFingerprintSha256.matches(Regex("^[0-9a-f]{64}$")))
        val observed = MessageDigest.getInstance("SHA-256")
            .digest(phoneHpkePublicKeyEncoded)
            .joinToString("") { "%02x".format(it) }
        require(
            MessageDigest.isEqual(
                observed.toByteArray(Charsets.US_ASCII),
                phoneHpkePublicKeyFingerprintSha256.toByteArray(Charsets.US_ASCII)
            )
        ) { "phone HPKE public key fingerprint mismatch" }
    }

    companion object {
        private const val MAX_HPKE_PUBLIC_KEY_BYTES = 4 * 1024
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
    val session: WearableHpkeDuplexSession
)

class WearableSecureHandshakeResponder(
    private val identityStore: WearableSecureIdentityStore,
    private val hpkeSessionFactory: WearableHpkeSessionFactory = TinkWearableHpkeSessionFactory()
) {
    @Throws(GeneralSecurityException::class)
    fun respond(request: WearableHandshakeRequest): WearableHandshakeResponderResult {
        val identity = identityStore.snapshot()
        val session = hpkeSessionFactory.create(request.phoneHpkePublicKeyEncoded.copyOf())
        val wearablePublicKey = session.wearablePublicKeyEncoded.copyOf()
        require(wearablePublicKey.isNotEmpty() && wearablePublicKey.size <= MAX_HPKE_PUBLIC_KEY_BYTES) {
            "wearable HPKE public key is invalid"
        }
        val expectedWearableFingerprint = MessageDigest.getInstance("SHA-256")
            .digest(wearablePublicKey)
            .joinToString("") { "%02x".format(it) }
        require(constantTimeEquals(expectedWearableFingerprint, session.wearableFingerprintSha256)) {
            "wearable HPKE public key fingerprint mismatch"
        }
        require(constantTimeEquals(request.phoneHpkePublicKeyFingerprintSha256, session.phoneFingerprintSha256)) {
            "phone HPKE session binding mismatch"
        }

        val transcript = WearableHandshakeTranscriptCodec.encode(
            stableId = request.stableId,
            keyFingerprintSha256 = identity.fingerprintSha256,
            sessionId = request.sessionId,
            protocolVersion = SECURE_PROTOCOL_VERSION,
            capabilities = request.capabilities.toSet(),
            challengeNonce = request.challengeNonce,
            issuedAtMs = request.issuedAtMs,
            phoneHpkePublicKeyFingerprintSha256 = request.phoneHpkePublicKeyFingerprintSha256,
            wearableHpkePublicKeyFingerprintSha256 = session.wearableFingerprintSha256
        )
        val signature = identityStore.sign(transcript)
        return WearableHandshakeResponderResult(
            response = WearableHandshakeResponse(
                identityPublicKeyX509 = identity.publicKeyX509.copyOf(),
                identityKeyFingerprintSha256 = identity.fingerprintSha256,
                wearableHpkePublicKeyEncoded = wearablePublicKey,
                wearableHpkePublicKeyFingerprintSha256 = session.wearableFingerprintSha256,
                signedTranscript = transcript.copyOf(),
                signature = signature.copyOf()
            ),
            session = session
        )
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            actual.toByteArray(Charsets.US_ASCII)
        )

    companion object {
        const val SECURE_PROTOCOL_VERSION = 2
        private const val MAX_HPKE_PUBLIC_KEY_BYTES = 4 * 1024
    }
}
