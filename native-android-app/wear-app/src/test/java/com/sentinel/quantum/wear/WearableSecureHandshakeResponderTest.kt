package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.security.WearableHandshakeTranscriptCodec
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertEquals
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
        override val publicKeyEncoded: ByteArray
    ) : WearableHpkeRecipientSession {
        override val fingerprintSha256: String = MessageDigest.getInstance("SHA-256")
            .digest(publicKeyEncoded)
            .joinToString("") { "%02x".format(it) }

        override fun decrypt(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray =
            throw UnsupportedOperationException("not needed by handshake test")
    }

    private class CountingHpkeFactory : WearableHpkeSessionFactory {
        var counter = 0
        override fun create(): WearableHpkeRecipientSession =
            FakeHpkeSession("watch-hpke-${++counter}".toByteArray())
    }

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
            phoneHpkePublicKeyFingerprintSha256 = "1".repeat(64)
        )

        val first = responder.respond(request)
        val second = responder.respond(request.copy(sessionId = "session-2"))

        assertNotEquals(first.response.wearableHpkePublicKeyFingerprintSha256,
            second.response.wearableHpkePublicKeyFingerprintSha256)
        assertFalse(first.session === second.session)

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
    fun refusesProtocolV1OrMalformedPhoneHpkeBinding() {
        val responder = WearableSecureHandshakeResponder(
            WearableSecureIdentityStore(MemoryIdentityBackend()),
            CountingHpkeFactory()
        )
        responder.respond(
            WearableHandshakeRequest(
                stableId = "watch-1",
                sessionId = "session-1",
                capabilities = setOf("SENTINEL_ALERTS"),
                challengeNonce = "AAAAAAAAAAAAAAAAAAAAAA",
                issuedAtMs = 100_000L,
                phoneHpkePublicKeyFingerprintSha256 = "invalid"
            )
        )
    }

    @Test
    fun responseDoesNotExposePrivateKeyBytes() {
        val fields = WearableHandshakeResponse::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(fields.any { it.contains("private") || it.contains("secret") })
        val sessionMethods = WearableHpkeRecipientSession::class.java.methods.map { it.name.lowercase() }
        assertFalse(sessionMethods.any { it.contains("private") || it.contains("secret") || it.contains("serialize") })
    }
}
