package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMeshGattTransportTest {
    private fun opened(
        maxFrameBytes: Int = 32,
        maxRetries: Int = 3
    ) = OfflineMeshGattTransport(
        maxFrames = 32,
        maxFrameBytes = maxFrameBytes,
        ackTimeoutMs = 100L,
        maxRetries = maxRetries,
        authenticator = OfflineMeshGattAuthenticator { _, proof ->
            proof.contentEquals(byteArrayOf(7))
        }
    ).also {
        assertTrue(it.startLink(1L))
        assertTrue(it.openAuthenticatedLink(1L, byteArrayOf(7)))
    }

    @Test fun privateTransportRemainsLockedWithoutAuthenticator() {
        val transport = OfflineMeshGattTransport()
        assertTrue(transport.startLink(1L))
        assertFalse(transport.openAuthenticatedLink(1L, byteArrayOf(7)))
        assertEquals(OfflineMeshGattTransport.Offer.LOCKED, transport.offer(byteArrayOf(1)))
    }

    @Test fun nextFragmentWaitsForRadioAndApplicationAck() {
        val sender = opened()
        val receiver = opened()

        assertEquals(OfflineMeshGattTransport.Offer.QUEUED, sender.offer(ByteArray(70) { it.toByte() }))
        val first = assertNotNull(sender.next(nowMs = 0L))
        assertEquals(0, first.fragmentIndex)
        assertTrue(sender.onRadioWriteComplete(first.ticket, first.generation, true, nowMs = 1L))
        assertNull(sender.next(nowMs = 2L))

        val inbound = receiver.receive(first.bytes, nowMs = 2L)
        assertTrue(inbound.accepted)
        val ack = assertNotNull(receiver.next(nowMs = 2L))
        assertFalse(ack.requiresAck)
        assertTrue(receiver.onRadioWriteComplete(ack.ticket, ack.generation, true, nowMs = 3L))
        assertTrue(sender.receive(ack.bytes, nowMs = 3L).accepted)

        val second = assertNotNull(sender.next(nowMs = 4L))
        assertEquals(1, second.fragmentIndex)
    }

    @Test fun ackTimeoutRetriesThenInterruptsAtBound() {
        val sender = opened(maxRetries = 2)
        assertEquals(OfflineMeshGattTransport.Offer.QUEUED, sender.offer(byteArrayOf(1)))
        val frame = assertNotNull(sender.next(nowMs = 0L))
        assertTrue(sender.onRadioWriteComplete(frame.ticket, frame.generation, true, nowMs = 1L))

        val retry = sender.tick(nowMs = 101L)
        assertTrue(retry is OfflineMeshGattTransport.Tick.Retry)
        assertEquals(2, (retry as OfflineMeshGattTransport.Tick.Retry).outbound.attempts)

        val failed = sender.tick(nowMs = 202L)
        assertTrue(failed is OfflineMeshGattTransport.Tick.Interrupted)
        assertEquals(OfflineMeshGattTransport.State.LOCKED, sender.state())
        assertEquals(0, sender.queuedCount())
    }

    @Test fun radioFailureAndStaleCallbacksInvalidateOnlyCurrentLink() {
        val sender = opened()
        assertEquals(OfflineMeshGattTransport.Offer.QUEUED, sender.offer(byteArrayOf(1)))
        val frame = assertNotNull(sender.next(nowMs = 0L))
        assertFalse(sender.onRadioWriteComplete(frame.ticket, frame.generation, false, nowMs = 1L))
        assertEquals(OfflineMeshGattTransport.State.LOCKED, sender.state())
        assertFalse(sender.onRadioWriteComplete(frame.ticket, frame.generation, true, nowMs = 2L))

        assertTrue(sender.startLink(2L))
        assertTrue(sender.openAuthenticatedLink(2L, byteArrayOf(7)))
        assertEquals(OfflineMeshGattTransport.Offer.QUEUED, sender.offer(byteArrayOf(2)))
        val current = assertNotNull(sender.next(nowMs = 3L))
        assertFalse(sender.onRadioWriteComplete(current.ticket, 1L, true, nowMs = 4L))
    }

    @Test fun receivingFragmentsDeliversOnceAndQueuesAck() {
        val sender = opened()
        val receiver = opened()
        sender.offer(ByteArray(70) { 5 })
        val outbound = assertNotNull(sender.next(0L))
        sender.onRadioWriteComplete(outbound.ticket, outbound.generation, true, 0L)
        val first = receiver.receive(outbound.bytes, 0L)
        assertTrue(first.accepted)
        val ack = assertNotNull(receiver.next(1L))
        receiver.onRadioWriteComplete(ack.ticket, ack.generation, true, 1L)
        assertNotNull(sender.receive(ack.bytes, 1L))
    }
}
