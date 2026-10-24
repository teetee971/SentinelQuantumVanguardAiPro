package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class OfflineMeshGattOutboxTest {
    private fun verifiedQueue(maxFrames: Int = 32, maxFrameBytes: Int = 244) =
        OfflineMeshGattOutbox(maxFrames, maxFrameBytes) { _, proof -> proof.contentEquals(byteArrayOf(9)) }
    private fun unlock(q: OfflineMeshGattOutbox) =
        assertTrue(q.openAuthenticatedLink(q.currentGeneration(), byteArrayOf(9)))

    @Test fun lockedByDefault() {
        val q = verifiedQueue()
        assertEquals(OfflineMeshGattOutbox.Offer.LOCKED, q.offer(byteArrayOf(1)))
        assertNull(q.next())
    }
    @Test fun boundedAndSingleInFlight() {
        val q = verifiedQueue(maxFrames=2,maxFrameBytes=3)
        unlock(q)
        assertEquals(OfflineMeshGattOutbox.Offer.QUEUED,q.offer(byteArrayOf(1,2)))
        assertEquals(OfflineMeshGattOutbox.Offer.QUEUED,q.offer(byteArrayOf(3)))
        assertEquals(OfflineMeshGattOutbox.Offer.FULL,q.offer(byteArrayOf(4)))
        val first = q.next()!!
        assertNull(q.next())
        assertTrue(q.complete(first.ticket,first.generation,true))
        assertNotNull(q.next())
    }
    @Test fun oversizedAndEmptyRejected() {
        val q = verifiedQueue(maxFrameBytes=2)
        unlock(q)
        assertEquals(OfflineMeshGattOutbox.Offer.INVALID_FRAME,q.offer(byteArrayOf()))
        assertEquals(OfflineMeshGattOutbox.Offer.INVALID_FRAME,q.offer(byteArrayOf(1,2,3)))
    }
    @Test fun bothInputAndOutputAreCopied() {
        val q = verifiedQueue()
        unlock(q)
        val bytes = byteArrayOf(7,8)
        q.offer(bytes)
        bytes[0] = 99
        val first = q.next()!!
        assertEquals(7, first.bytes[0].toInt())
        first.bytes[0] = 44
        assertTrue(q.complete(first.ticket,first.generation,true))
    }
    @Test fun staleCallbackCannotCompleteNewSession() {
        val q = verifiedQueue()
        unlock(q)
        q.offer(byteArrayOf(1))
        val old = q.next()!!
        q.closeLink()
        unlock(q)
        q.offer(byteArrayOf(2))
        val current = q.next()!!
        assertFalse(q.complete(old.ticket,old.generation,true))
        assertEquals(1,q.queuedCount())
        assertTrue(q.complete(current.ticket,current.generation,true))
    }
    @Test fun failedRadioCallbackInvalidatesLink() {
        val q = verifiedQueue()
        unlock(q)
        q.offer(byteArrayOf(1))
        q.offer(byteArrayOf(2))
        val frame = q.next()!!
        assertFalse(q.complete(frame.ticket,frame.generation,false))
        assertEquals(0,q.queuedCount())
        assertEquals(OfflineMeshGattOutbox.Offer.LOCKED,q.offer(byteArrayOf(3)))
    }
    @Test fun defaultVerifierCannotBeBypassed() {
        val q = OfflineMeshGattOutbox()
        assertFalse(q.openAuthenticatedLink(q.currentGeneration(), byteArrayOf(9)))
        assertEquals(OfflineMeshGattOutbox.Offer.LOCKED, q.offer(byteArrayOf(1)))
    }
    @Test fun staleGenerationAndWrongProofRejected() {
        val q = verifiedQueue()
        assertFalse(q.openAuthenticatedLink(q.currentGeneration(), byteArrayOf(8)))
        val old = q.currentGeneration()
        q.closeLink()
        assertFalse(q.openAuthenticatedLink(old, byteArrayOf(9)))
        assertEquals(OfflineMeshGattOutbox.Offer.LOCKED, q.offer(byteArrayOf(1)))
    }
}
