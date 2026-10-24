package com.sentinel.quantum.security

import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMeshGattCryptoTest {
    @Test
    fun mutualHandshakeAuthenticatesBeforeApplicationData() {
        val initiatorKeys = ecKeyPair()
        val responderKeys = ecKeyPair()
        val initiator = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.INITIATOR,
            identity(initiatorKeys, responderKeys)
        )
        val responder = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.RESPONDER,
            identity(responderKeys, initiatorKeys)
        )

        val hello = requireNotNull(initiator.start())
        assertFalse(initiator.isEstablished())
        val response = requireNotNull(responder.receive(hello).outbound)
        assertFalse(responder.isEstablished())
        val finish = requireNotNull(initiator.receive(response).outbound)
        assertTrue(initiator.isEstablished())
        assertTrue(responder.receive(finish).accepted)
        assertTrue(responder.isEstablished())

        val plaintext = "private mesh payload".toByteArray(StandardCharsets.UTF_8)
        val envelope = initiator.encrypt(plaintext, 1L)
        assertArrayEquals(plaintext, responder.decrypt(envelope))
        assertNull("a ciphertext must not be accepted twice", responder.decrypt(envelope))
    }

    @Test
    fun untrustedIdentityCannotCompleteHandshake() {
        val initiatorKeys = ecKeyPair()
        val responderKeys = ecKeyPair()
        val initiator = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.INITIATOR,
            identity(initiatorKeys, responderKeys)
        )
        val responder = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.RESPONDER,
            identity(responderKeys, emptyList<KeyPair>())
        )

        val result = responder.receive(requireNotNull(initiator.start()))
        assertFalse(result.accepted)
        assertFalse(responder.isEstablished())
    }

    @Test
    fun transcriptAndCiphertextTamperingAreRejected() {
        val initiatorKeys = ecKeyPair()
        val responderKeys = ecKeyPair()
        val initiator = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.INITIATOR,
            identity(initiatorKeys, responderKeys)
        )
        val responder = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.RESPONDER,
            identity(responderKeys, initiatorKeys)
        )
        val hello = requireNotNull(initiator.start())
        val response = requireNotNull(responder.receive(hello).outbound)
        val tamperedResponse = response.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0x01).toByte() }
        assertFalse(initiator.receive(tamperedResponse).accepted)
        assertFalse(initiator.isEstablished())

        val initiator2 = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.INITIATOR,
            identity(initiatorKeys, responderKeys)
        )
        val responder2 = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.RESPONDER,
            identity(responderKeys, initiatorKeys)
        )
        val response2 = requireNotNull(responder2.receive(requireNotNull(initiator2.start())).outbound)
        val finish = requireNotNull(initiator2.receive(response2).outbound)
        assertTrue(responder2.receive(finish).accepted)
        val envelope = initiator2.encrypt("secret".toByteArray(StandardCharsets.UTF_8), 1L)
        envelope[envelope.lastIndex] = (envelope[envelope.lastIndex].toInt() xor 0x01).toByte()
        assertNull(responder2.decrypt(envelope))
    }

    @Test
    fun encryptRejectsNonceCounterReuseWithinOneSession() {
        val initiatorKeys = ecKeyPair()
        val responderKeys = ecKeyPair()
        val initiator = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.INITIATOR,
            identity(initiatorKeys, responderKeys)
        )
        val responder = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.RESPONDER,
            identity(responderKeys, initiatorKeys)
        )
        val response = requireNotNull(responder.receive(requireNotNull(initiator.start())).outbound)
        val finish = requireNotNull(initiator.receive(response).outbound)
        assertTrue(responder.receive(finish).accepted)

        initiator.encrypt("first".toByteArray(StandardCharsets.UTF_8), 1L)
        assertThrows(IllegalArgumentException::class.java) {
            initiator.encrypt("reused".toByteArray(StandardCharsets.UTF_8), 1L)
        }
    }

    @Test
    fun defaultIdentityIsNotAvailableWithoutExplicitTrust() {
        val session = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.INITIATOR,
            null
        )
        assertNull(session.start())
        assertFalse(session.isEstablished())
    }

    @Test
    fun identityRejectsNonP256PublicKeys() {
        val nonP256 = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp384r1"))
        }.generateKeyPair()
        assertThrows(IllegalArgumentException::class.java) {
            OfflineMeshGattCryptoIdentity.fromKeyPair(nonP256) { true }
        }
    }

    @Test
    fun identityRejectsSignerThatDoesNotMatchAdvertisedKey() {
        val advertised = ecKeyPair()
        val wrongSigner = ecKeyPair().private
        assertThrows(IllegalArgumentException::class.java) {
            OfflineMeshGattCryptoIdentity.fromSigner(
                advertised.public.encoded,
                { bytes ->
                    Signature.getInstance("SHA256withECDSA").run {
                        initSign(wrongSigner)
                        update(bytes)
                        sign()
                    }
                },
                { true }
            )
        }
    }

    @Test
    fun resetCreatesFreshEphemeralHandshakeMaterial() {
        val session = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.INITIATOR,
            identity(ecKeyPair(), emptyList<KeyPair>())
        )
        val first = requireNotNull(session.start())
        session.reset()
        val second = requireNotNull(session.start())
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun revokedPeerIsRejectedOnTheNextSession() {
        val initiatorKeys = ecKeyPair()
        val responderKeys = ecKeyPair()
        var trusted = true
        val initiator = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.INITIATOR,
            identity(initiatorKeys, responderKeys)
        )
        val responder = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.RESPONDER,
            OfflineMeshGattCryptoIdentity.fromKeyPair(responderKeys) { candidate ->
                trusted && MessageDigest.isEqual(initiatorKeys.public.encoded, candidate)
            }
        )
        val hello = requireNotNull(initiator.start())
        assertTrue(responder.receive(hello).accepted)
        trusted = false
        responder.reset()
        assertFalse(responder.receive(hello).accepted)
    }

    @Test
    fun malformedEcPointIsRejectedWithoutEscapingAsAnException() {
        val initiator = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.INITIATOR,
            identity(ecKeyPair(), emptyList<KeyPair>())
        )
        val responder = OfflineMeshGattCryptoSession(
            OfflineMeshGattCryptoSession.Role.RESPONDER,
            identity(ecKeyPair(), emptyList<KeyPair>())
        )
        val malformed = requireNotNull(initiator.start()).copyOf().also {
            // Control header (7), version (1), identity length (2), then the
            // uncompressed EC point. Zeroing X makes the point invalid.
            for (index in 11 until 43) it[index] = 0
        }
        assertFalse(responder.receive(malformed).accepted)
        assertFalse(responder.isEstablished())
    }

    private fun ecKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(256)
    }.generateKeyPair()

    private fun identity(
        keyPair: KeyPair,
        trusted: Any
    ): OfflineMeshGattCryptoIdentity {
        val trustedKeys = when (trusted) {
            is KeyPair -> listOf(trusted.public.encoded)
            is List<*> -> trusted.filterIsInstance<KeyPair>().map { it.public.encoded }
            else -> error("unsupported trust fixture")
        }
        return OfflineMeshGattCryptoIdentity.fromKeyPair(keyPair) { candidate ->
            trustedKeys.any { MessageDigest.isEqual(it, candidate) }
        }
    }
}
