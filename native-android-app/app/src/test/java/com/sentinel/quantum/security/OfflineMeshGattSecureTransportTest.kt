package com.sentinel.quantum.security

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMeshGattSecureTransportTest {
    @Test
    fun authenticatedChannelsExchangeFragmentedEncryptedMessagesBothWays() {
        val initiatorKeys = keyPair()
        val responderKeys = keyPair()
        val initiator = channel(
            OfflineMeshGattCryptoSession.Role.INITIATOR,
            initiatorKeys,
            responderKeys
        )
        val responder = channel(
            OfflineMeshGattCryptoSession.Role.RESPONDER,
            responderKeys,
            initiatorKeys
        )
        assertTrue(initiator.startLink(1L))
        assertTrue(responder.startLink(1L))

        val hello = requireNotNull(initiator.next(0L))
        assertTrue(hello.isControl)
        assertTrue(initiator.onRadioWriteComplete(hello.ticket, 1L, true, 1L))
        var event = responder.receive(hello.bytes, 2L)
        assertTrue(event.accepted)
        val response = requireNotNull(responder.next(2L))
        assertTrue(response.isControl)
        assertTrue(responder.onRadioWriteComplete(response.ticket, 1L, true, 3L))
        event = initiator.receive(response.bytes, 4L)
        assertTrue(event.accepted)
        val finish = requireNotNull(initiator.next(4L))
        assertTrue(finish.isControl)
        assertTrue(initiator.onRadioWriteComplete(finish.ticket, 1L, true, 5L))
        event = responder.receive(finish.bytes, 6L)
        assertTrue(event.accepted)
        assertTrue(initiator.isAuthenticated())
        assertTrue(responder.isAuthenticated())

        val payload = ByteArray(500) { (it % 251).toByte() }
        assertEquals(OfflineMeshGattTransport.Offer.QUEUED, initiator.offer(payload))
        var received: ByteArray? = null
        var now = 10L
        while (received == null) {
            val outbound = requireNotNull(initiator.next(now))
            assertFalse(outbound.isControl)
            assertTrue(initiator.onRadioWriteComplete(outbound.ticket, 1L, true, now + 1L))
            val inbound = responder.receive(outbound.bytes, now + 2L)
            assertTrue(inbound.accepted)
            if (inbound.complete != null) received = inbound.complete
            val ack = requireNotNull(responder.next(now + 2L))
            assertTrue(ack.isControl.not())
            assertTrue(responder.onRadioWriteComplete(ack.ticket, 1L, true, now + 3L))
            val ackResult = initiator.receive(ack.bytes, now + 4L)
            assertTrue(ackResult.accepted)
            now += 10L
        }
        assertArrayEquals(payload, received)

        val reverse = "response".toByteArray()
        assertEquals(OfflineMeshGattTransport.Offer.QUEUED, responder.offer(reverse))
        val reverseFrame = requireNotNull(responder.next(now))
        assertTrue(responder.onRadioWriteComplete(reverseFrame.ticket, 1L, true, now + 1L))
        assertArrayEquals(reverse, initiator.receive(reverseFrame.bytes, now + 2L).complete)
    }

    @Test
    fun unauthenticatedChannelNeverQueuesPrivateData() {
        val keys = keyPair()
        val channel = OfflineMeshGattSecureTransport(
            OfflineMeshGattCryptoSession(
                OfflineMeshGattCryptoSession.Role.INITIATOR,
                OfflineMeshGattCryptoIdentity.fromKeyPair(keys) { false }
            )
        )
        assertTrue(channel.startLink(1L))
        assertEquals(OfflineMeshGattTransport.Offer.LOCKED, channel.offer(byteArrayOf(1)))
        assertFalse(channel.isAuthenticated())
    }

    @Test
    fun staleGenerationCannotCompleteAWrite() {
        val initiatorKeys = keyPair()
        val responderKeys = keyPair()
        val channel = channel(
            OfflineMeshGattCryptoSession.Role.INITIATOR,
            initiatorKeys,
            responderKeys
        )
        assertTrue(channel.startLink(4L))
        val hello = requireNotNull(channel.next(0L))
        assertFalse(channel.onRadioWriteComplete(hello.ticket, 3L, true, 1L))
        assertTrue(channel.onRadioWriteComplete(hello.ticket, 4L, true, 1L))
    }

    private fun channel(
        role: OfflineMeshGattCryptoSession.Role,
        local: KeyPair,
        peer: KeyPair
    ): OfflineMeshGattSecureTransport = OfflineMeshGattSecureTransport(
        OfflineMeshGattCryptoSession(
            role,
            OfflineMeshGattCryptoIdentity.fromKeyPair(local) { candidate ->
                MessageDigest.isEqual(peer.public.encoded, candidate)
            }
        )
    )

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(256)
    }.generateKeyPair()
}
