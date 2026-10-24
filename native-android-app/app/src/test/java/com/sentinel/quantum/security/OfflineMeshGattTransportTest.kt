package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
        val first = sender.next(nowMs = 0L) ?: error("first frame missing")
        assertEquals(0, first.fragmentIndex)
        assertTrue(sender.onRadioWriteComplete(first.ticket, first.generation, true, nowMs = 1L))
        assertNull(sender.next(nowMs = 2L))

        val inbound = receiver.receive(first.bytes, nowMs = 2L)
        assertTrue(inbound.accepted)
        val ack = receiver.next(nowMs = 2L) ?: error("ack frame missing")
        assertFalse(ack.requiresAck)
        assertTrue(receiver.onRadioWriteComplete(ack.ticket, ack.generation, true, nowMs = 3L))
        assertTrue(sender.receive(ack.bytes, nowMs = 3L).accepted)

        val second = sender.next(nowMs = 4L) ?: error("second frame missing")
        assertEquals(1, second.fragmentIndex)
    }

    @Test fun ackTimeoutRetriesThenInterruptsAtBound() {
        val sender = opened(maxRetries = 2)
        assertEquals(OfflineMeshGattTransport.Offer.QUEUED, sender.offer(byteArrayOf(1)))
        val frame = sender.next(nowMs = 0L) ?: error("frame missing")
        assertTrue(sender.onRadioWriteComplete(frame.ticket, frame.generation, true, nowMs = 1L))

        val retry = sender.tick(nowMs = 101L)
        assertTrue(retry is OfflineMeshGattTransport.Tick.Retry)
        assertEquals(2, (retry as OfflineMeshGattTransport.Tick.Retry).outbound.attempts)

        val retried = (retry as OfflineMeshGattTransport.Tick.Retry).outbound
        assertTrue(sender.onRadioWriteComplete(
            retried.ticket, retried.generation, true, nowMs = 102L
        ))

        val failed = sender.tick(nowMs = 202L)
        assertTrue(failed is OfflineMeshGattTransport.Tick.Interrupted)
        assertEquals(OfflineMeshGattTransport.State.LOCKED, sender.state())
        assertEquals(0, sender.queuedCount())
    }

    @Test fun radioFailureAndStaleCallbacksInvalidateOnlyCurrentLink() {
        val sender = opened()
        assertEquals(OfflineMeshGattTransport.Offer.QUEUED, sender.offer(byteArrayOf(1)))
        val frame = sender.next(nowMs = 0L) ?: error("frame missing")
        assertFalse(sender.onRadioWriteComplete(frame.ticket, frame.generation, false, nowMs = 1L))
        assertEquals(OfflineMeshGattTransport.State.LOCKED, sender.state())
        assertFalse(sender.onRadioWriteComplete(frame.ticket, frame.generation, true, nowMs = 2L))

        assertTrue(sender.startLink(2L))
        assertTrue(sender.openAuthenticatedLink(2L, byteArrayOf(7)))
        assertEquals(OfflineMeshGattTransport.Offer.QUEUED, sender.offer(byteArrayOf(2)))
        val current = sender.next(nowMs = 3L) ?: error("current frame missing")
        assertFalse(sender.onRadioWriteComplete(current.ticket, 1L, true, nowMs = 4L))
    }

    @Test fun receivingFragmentsDeliversOnceAndQueuesAck() {
        val sender = opened()
        val receiver = opened()
        sender.offer(ByteArray(70) { 5 })
        val outbound = sender.next(0L) ?: error("outbound frame missing")
        assertTrue(sender.onRadioWriteComplete(outbound.ticket, outbound.generation, true, 0L))
        val first = receiver.receive(outbound.bytes, 0L)
        assertTrue(first.accepted)
        val ack = receiver.next(1L) ?: error("ack frame missing")
        assertTrue(receiver.onRadioWriteComplete(ack.ticket, ack.generation, true, 1L))
        assertTrue(sender.receive(ack.bytes, 1L).accepted)
    }

    @Test fun concurrentOffersRemainBoundedAndDoNotCorruptTickets() {
        val transport = opened(maxFrameBytes = 32)
        val workers = Executors.newFixedThreadPool(8)
        try {
            val results = (1..128).map {
                workers.submit<OfflineMeshGattTransport.Offer> {
                    transport.offer(byteArrayOf(it.toByte()))
                }
            }.map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(results.any { it == OfflineMeshGattTransport.Offer.QUEUED })
            assertTrue(transport.queuedCount() <= 32)
            val tickets = mutableSetOf<Long>()
            repeat(32) {
                transport.next(it.toLong())?.let { outbound ->
                    assertTrue(tickets.add(outbound.ticket))
                    transport.onRadioWriteComplete(outbound.ticket, outbound.generation, false, it.toLong())
                }
            }
        } finally {
            workers.shutdownNow()
        }
    }
}
