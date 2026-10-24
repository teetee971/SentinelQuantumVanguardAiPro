package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security regression gate for the pure Kotlin transport.
 *
 * These tests DO NOT prove peer identity, cryptographic authentication,
 * Android CCCD subscription, or a real BLE data path.
 */
class OfflineMeshGattSecurityBoundaryTest {
    private fun frame(id: Long = 1L): ByteArray = OfflineMeshGattFrameCodec.encode(
        OfflineMeshGattFrameCodec.Frame.Data(id, 0, 1, byteArrayOf(42))
    )

    @Test fun defaultAuthenticatorNeverOpensPrivateLink() {
        val link = OfflineMeshGattTransport()
        assertTrue(link.startLink(1L))
        assertFalse(link.openAuthenticatedLink(1L, byteArrayOf(7)))
        assertEquals(OfflineMeshGattTransport.State.LOCKED, link.state())
        assertEquals(OfflineMeshGattTransport.Offer.LOCKED, link.offer(byteArrayOf(42)))
        assertFalse(link.receive(frame(), 0L).accepted)
        assertNull(link.next(0L))
    }

    @Test fun authenticatedTestLinkIsRevokedOnInterrupt() {
        val link = OfflineMeshGattTransport(
            authenticator = OfflineMeshGattAuthenticator { generation, proof ->
                generation == 1L && proof.contentEquals(byteArrayOf(7))
            }
        )
        assertTrue(link.startLink(1L))
        assertTrue(link.openAuthenticatedLink(1L, byteArrayOf(7)))
        assertTrue(link.receive(frame(), 0L).accepted)
        link.interrupt()
        assertEquals(OfflineMeshGattTransport.State.LOCKED, link.state())
        assertFalse(link.receive(frame(), 1L).accepted)
        assertEquals(OfflineMeshGattTransport.Offer.LOCKED, link.offer(byteArrayOf(42)))
        assertNull(link.next(1L))
        assertTrue(link.startLink(2L))
        assertFalse(link.openAuthenticatedLink(1L, byteArrayOf(7)))
        assertFalse(link.openAuthenticatedLink(2L, byteArrayOf(7)))
    }

    @Test fun malformedFramesCannotBecomeInboundDataOrAcks() {
        val link = OfflineMeshGattTransport(
            authenticator = OfflineMeshGattAuthenticator { _, proof ->
                proof.contentEquals(byteArrayOf(7))
            }
        )
        assertTrue(link.startLink(1L))
        assertTrue(link.openAuthenticatedLink(1L, byteArrayOf(7)))
        assertFalse(link.receive(byteArrayOf(0x53, 0x4d), 0L).accepted)
        assertFalse(link.receive(ByteArray(245), 1L).accepted)
        assertNull(link.next(2L))
    }
}
