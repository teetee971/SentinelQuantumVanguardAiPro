package com.sentinel.quantum.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMeshGattReassemblerTest {
    private fun frames(payload: ByteArray) =
        OfflineMeshGattFrameCodec.fragment(11L, payload, maxFrameBytes = 32)
            .map { OfflineMeshGattFrameCodec.decode(it, 32) as OfflineMeshGattFrameCodec.Frame.Data }

    @Test fun outOfOrderFragmentsDeliverExactlyOnce() {
        val fragments = frames(ByteArray(70) { (it + 1).toByte() })
        val reassembler = OfflineMeshGattReassembler(fragmentTimeoutMs = 1000L)

        assertFalse(reassembler.accept(fragments[1], nowMs = 10L).complete != null)
        assertFalse(reassembler.accept(fragments[0], nowMs = 20L).complete != null)
        val completed = reassembler.accept(fragments[2], nowMs = 30L)

        assertArrayEquals(ByteArray(70) { (it + 1).toByte() }, completed.complete)
        assertTrue(completed.ack != null)
    }

    @Test fun duplicatesAreAckedButNotDeliveredTwice() {
        val fragment = frames(byteArrayOf(3, 4, 5)).single()
        val reassembler = OfflineMeshGattReassembler()

        val first = reassembler.accept(fragment, nowMs = 0L)
        val duplicate = reassembler.accept(fragment, nowMs = 1L)

        assertTrue(first.complete != null)
        assertTrue(duplicate.accepted)
        assertTrue(duplicate.duplicate)
        assertEquals(null, duplicate.complete)
        assertTrue(duplicate.ack != null)
    }

    @Test fun expiredAssembliesAreRemovedAndCanBeRestarted() {
        val fragments = frames(ByteArray(60) { 1 })
        val reassembler = OfflineMeshGattReassembler(fragmentTimeoutMs = 100L)

        reassembler.accept(fragments[0], nowMs = 0L)
        assertEquals(listOf(11L), reassembler.expire(nowMs = 101L))
        assertTrue(reassembler.accept(fragments[0], nowMs = 102L).accepted)
    }
}
