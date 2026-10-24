package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class OfflineMeshGattOutboxTest {
    @Test fun lockedByDefault() {
        val q = OfflineMeshGattOutbox()
        assertEquals(OfflineMeshGattOutbox.Offer.LOCKED, q.offer(byteArrayOf(1)))
        assertNull(q.next())
    }
    @Test fun boundedAndSingleInFlight() {
        val q = OfflineMeshGattOutbox(maxFrames=2,maxFrameBytes=3)
        q.openAuthenticatedLink()
        assertEquals(OfflineMeshGattOutbox.Offer.QUEUED,q.offer(byteArrayOf(1,2)))
        assertEquals(OfflineMeshGattOutbox.Offer.QUEUED,q.offer(byteArrayOf(3)))
        assertEquals(OfflineMeshGattOutbox.Offer.FULL,q.offer(byteArrayOf(4)))
        val first = q.next()!!
        assertNull(q.next())
        assertTrue(q.complete(first.ticket,first.generation,true))
        assertNotNull(q.next())
    }
    @Test fun oversizedAndEmptyRejected() {
        val q = OfflineMeshGattOutbox(maxFrameBytes=2)
        q.openAuthenticatedLink()
        assertEquals(OfflineMeshGattOutbox.Offer.INVALID_FRAME,q.offer(byteArrayOf()))
        assertEquals(OfflineMeshGattOutbox.Offer.INVALID_FRAME,q.offer(byteArrayOf(1,2,3)))
    }
    @Test fun bothInputAndOutputAreCopied() {
        val q = OfflineMeshGattOutbox()
        q.openAuthenticatedLink()
        val bytes = byteArrayOf(7,8)
        q.offer(bytes)
        bytes[0] = 99
        val first = q.next()!!
        assertEquals(7, first.bytes[0].toInt())
        first.bytes[0] = 44
        assertTrue(q.complete(first.ticket,first.generation,true))
    }
    @Test fun staleCallbackCannotCompleteNewSession() {
        val q = OfflineMeshGattOutbox()
        q.openAuthenticatedLink()
        q.offer(byteArrayOf(1))
        val old = q.next()!!
        q.closeLink()
        q.openAuthenticatedLink()
        q.offer(byteArrayOf(2))
        val current = q.next()!!
        assertFalse(q.complete(old.ticket,old.generation,true))
        assertEquals(1,q.queuedCount())
        assertTrue(q.complete(current.ticket,current.generation,true))
    }
    @Test fun failedRadioCallbackInvalidatesLink() {
        val q = OfflineMeshGattOutbox()
        q.openAuthenticatedLink()
        q.offer(byteArrayOf(1))
        q.offer(byteArrayOf(2))
        val frame = q.next()!!
        assertFalse(q.complete(frame.ticket,frame.generation,false))
        assertEquals(0,q.queuedCount())
        assertEquals(OfflineMeshGattOutbox.Offer.LOCKED,q.offer(byteArrayOf(3)))
    }
}
