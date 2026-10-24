package com.sentinel.quantum.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMeshGattFrameTest {
    @Test fun fragmentsPayloadWithinConfiguredFrameLimit() {
        val payload = ByteArray(500) { it.toByte() }
        val encoded = OfflineMeshGattFrameCodec.fragment(
            messageId = 7L,
            payload = payload,
            maxFrameBytes = 64
        )

        assertTrue(encoded.size > 1)
        val decoded = encoded.map {
            OfflineMeshGattFrameCodec.decode(it, maxFrameBytes = 64)
                as OfflineMeshGattFrameCodec.Frame.Data
        }
        assertTrue(decoded.all { it.fragmentCount == encoded.size })
        assertEquals(decoded.indices.toList(), decoded.map { it.fragmentIndex })
        assertArrayEquals(payload, decoded.flatMap { it.payload.asIterable() }.toByteArray())
    }

    @Test fun dataAndAckFramesRoundTripWithCopies() {
        val data = OfflineMeshGattFrameCodec.Frame.Data(
            messageId = 9L,
            fragmentIndex = 1,
            fragmentCount = 3,
            payload = byteArrayOf(1, 2, 3)
        )
        val decodedData = OfflineMeshGattFrameCodec.decode(
            OfflineMeshGattFrameCodec.encode(data)
        ) as OfflineMeshGattFrameCodec.Frame.Data
        assertArrayEquals(data.payload, decodedData.payload)

        val ack = OfflineMeshGattFrameCodec.Frame.Ack(messageId = 9L, fragmentIndex = 1)
        val decodedAck = OfflineMeshGattFrameCodec.decode(
            OfflineMeshGattFrameCodec.encode(ack)
        ) as OfflineMeshGattFrameCodec.Frame.Ack
        assertEquals(ack, decodedAck)
    }

    @Test fun malformedAndOversizedFramesAreRejected() {
        val encoded = OfflineMeshGattFrameCodec.encode(
            OfflineMeshGattFrameCodec.Frame.Data(4L, 0, 1, byteArrayOf(8, 9))
        )
        encoded[12] = 0
        encoded[13] = 1

        assertNull(OfflineMeshGattFrameCodec.decode(encoded))
        assertNull(OfflineMeshGattFrameCodec.decode(ByteArray(245)))
        assertNull(OfflineMeshGattFrameCodec.decode(byteArrayOf(0x53, 0x4d)))
    }
}
