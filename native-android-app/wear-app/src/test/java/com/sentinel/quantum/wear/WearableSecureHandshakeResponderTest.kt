package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.security.WearableHandshakeTranscriptCodec
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableSecureHandshakeResponderTest {
    private class MemoryIdentityBackend : WearableIdentityBackend {
        private val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        override fun loadOrCreate(): WearableSigningIdentity = object : WearableSigningIdentity {
            override val publicKeyX509: ByteArray get() = pair.public.encoded.copyOf()
            override fun sign(message: ByteArray): ByteArray = Signature.getInstance("Ed25519").run {
                initSign(pair.private)
                update(message)
                sign()
            }
        }
        override fun reset() = Unit
    }

    private class FakeHpkeSession(
        override val wearablePublicKeyEncoded: ByteArray,
        override val phonePublicKeyEncoded: ByteArray
    ) : WearableHpkeDuplexSession {
        override val wearableFingerprintSha256: String = sha256(wearablePublicKeyEncoded)
        override val phoneFingerprintSha256: String = sha256(phonePublicKeyEncoded)
        override fun encryptForPhone(plaintext: ByteArray, contextInfo: ByteArray): ByteArray = plaintext.copyOf()
        override fun decryptFromPhone(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray = ciphertext.copyOf()

        companion object {
            private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }

    private class CountingHpkeFactory : WearableHpkeSessionFactory {
        var counter = 0
        override fun create(phoneHpkePublicKeyEncoded: ByteArray): WearableHpkeDuplexSession =
            FakeHpkeSession("watch-hpke-${++counter}".toByteArray(), phoneHpkePublicKeyEncoded.copyOf())
    }

    private val phoneHpkePublicKeyEncoded = "phone-public-hpke".toByteArray()
    private val phoneHpkeFingerprint = MessageDigest.getInstance("SHA-256")
        .digest(phoneHpkePublicKeyEncoded).joinToString("") { "%02x".format(it) }

    @Test
    fun newSessionGetsFreshHpkeBindingAndAuthenticTranscript() {
        val identityStore = WearableSecureIdentityStore(MemoryIdentityBackend())
        val hpkeFactory = CountingHpkeFactory()
        val responder = WearableSecureHandshakeResponder(identityStore, hpkeFactory)
        val request = WearableHandshakeRequest(
            stableId = "watch-1",
            sessionId = "session-1",
            capabilities = setOf("SENTINEL_ALERTS"),
            challengeNonce = "AAAAAAAAAAAAAAAAAAAAAA",
            issuedAtMs = 100_000L,
            phoneHpkePublicKeyEncoded = phoneHpkePublicKeyEncoded,
            phoneHpkePublicKeyFingerprintSha256 = phoneHpkeFingerprint
        )

        val first = responder.respond(request)
        val second = responder.respond(request.copy(sessionId = "session-2"))

        assertNotEquals(first.response.wearableHpkePublicKeyFingerprintSha256,
            second.response.wearableHpkePublicKeyFingerprintSha256)
        assertFalse(first.session === second.session)
        assertTrue(first.session.phonePublicKeyEncoded.contentEquals(phoneHpkePublicKeyEncoded))

        val identity = identityStore.snapshot()
        val canonical = WearableHandshakeTranscriptCodec.encode(
            stableId = request.stableId,
            keyFingerprintSha256 = identity.fingerprintSha256,
            sessionId = request.sessionId,
            protocolVersion = 2,
            capabilities = request.capabilities,
            challengeNonce = request.challengeNonce,
            issuedAtMs = request.issuedAtMs,
            phoneHpkePublicKeyFingerprintSha256 = request.phoneHpkePublicKeyFingerprintSha256,
            wearableHpkePublicKeyFingerprintSha256 = first.response.wearableHpkePublicKeyFingerprintSha256
        )
        assertTrue(canonical.contentEquals(first.response.signedTranscript))
        val publicKey = java.security.KeyFactory.getInstance("Ed25519")
            .generatePublic(java.security.spec.X509EncodedKeySpec(first.response.identityPublicKeyX509))
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(first.response.signedTranscript)
        assertTrue(verifier.verify(first.response.signature))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesMismatchedPhoneHpkeBinding() {
        WearableHandshakeRequest(
            stableId = "watch-1",
            sessionId = "session-1",
            capabilities = setOf("SENTINEL_ALERTS"),
            challengeNonce = "AAAAAAAAAAAAAAAAAAAAAA",
            issuedAtMs = 100_000L,
            phoneHpkePublicKeyEncoded = phoneHpkePublicKeyEncoded,
            phoneHpkePublicKeyFingerprintSha256 = "1".repeat(64)
        )
    }

    @Test
    fun responseDoesNotExposePrivateKeyBytes() {
        val fields = WearableHandshakeResponse::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fields.any { it.contains("private") || it.contains("secret") })
        val sessionMethods = WearableHpkeDuplexSession::class.java.methods.map { it.name.lowercase() }
        assertFalse(sessionMethods.any { it.contains("private") || it.contains("secret") || it.contains("serialize") })
    }
}
