package com.sentinel.quantum.security

import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
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
        val envelope = initiator2.encrypt("secret".toByteArray(StandardCharsets.UTF_8), 7L)
        envelope[envelope.lastIndex] = (envelope[envelope.lastIndex].toInt() xor 0x01).toByte()
        assertNull(responder2.decrypt(envelope))
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
