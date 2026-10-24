package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class OfflineMeshGattLinkGateTest {
    @Test fun lockedUntilAllTransportPhasesSucceed() {
        val gate = OfflineMeshGattLinkGate()
        val id = gate.begin()!!
        assertFalse(gate.mayWrite(id, 1))
        assertTrue(gate.connected(id, true))
        assertFalse(gate.mayWrite(id, 1))
        assertTrue(gate.servicesDiscovered(id, true))
        assertFalse(gate.mayWrite(id, 1))
        assertTrue(gate.mtuResolved(id, 247, true))
        assertEquals(244, gate.maxWriteBytes())
        assertTrue(gate.mayWrite(id, 244))
        assertFalse(gate.mayWrite(id, 245))
    }

    @Test fun defaultMtuCapsWritesAtTwentyBytes() {
        val gate = OfflineMeshGattLinkGate()
        val id = gate.begin()!!
        gate.connected(id, true)
        gate.servicesDiscovered(id, true)
        assertTrue(gate.mtuResolved(id, 23, true))
        assertTrue(gate.mayWrite(id, 20))
        assertFalse(gate.mayWrite(id, 21))
        assertFalse(gate.mayWrite(id, 0))
    }

    @Test fun invalidDiscoveryClosesAndInvalidatesCallbacks() {
        val gate = OfflineMeshGattLinkGate()
        val old = gate.begin()!!
        gate.connected(old, true)
        assertFalse(gate.servicesDiscovered(old, false))
        val current = gate.begin()!!
        assertFalse(gate.connected(old, true))
        assertFalse(gate.mayWrite(old, 1))
        assertTrue(gate.connected(current, true))
    }

    @Test fun failedNegotiationClosesLink() {
        val gate = OfflineMeshGattLinkGate()
        val id = gate.begin()!!
        gate.connected(id, true)
        gate.servicesDiscovered(id, true)
        assertFalse(gate.mtuResolved(id, 10, true))
        assertEquals(OfflineMeshGattLinkGate.Stage.CLOSED, gate.stage())
    }

    @Test fun callbacksAfterCloseCannotReopenLink() {
        val gate = OfflineMeshGattLinkGate()
        val id = gate.begin()!!
        gate.close()
        assertFalse(gate.connected(id, true))
        assertFalse(gate.servicesDiscovered(id, true))
        assertFalse(gate.mtuResolved(id, 247, true))
    }

    @Test fun secondBeginRejectedWhileConnecting() {
        val gate = OfflineMeshGattLinkGate()
        val id = gate.begin()!!
        assertNull(gate.begin())
        assertEquals(id, gate.generation())
    }
    @Test fun duplicatedConnectionCallbackCannotRestartServiceDiscovery() {
        val gate = OfflineMeshGattLinkGate()
        val id = gate.begin()!!
        assertTrue(gate.connected(id, true))
        assertFalse(gate.connected(id, true))
        assertEquals(OfflineMeshGattLinkGate.Stage.DISCOVERING, gate.stage())
    }

    @Test fun lateMtuCallbackAfterReadyCannotChangePayloadLimit() {
        val gate = OfflineMeshGattLinkGate()
        val id = gate.begin()!!
        assertTrue(gate.connected(id, true))
        assertTrue(gate.servicesDiscovered(id, true))
        assertTrue(gate.mtuResolved(id, 247, true))
        assertFalse(gate.mtuResolved(id, 517, true))
        assertEquals(244, gate.maxWriteBytes())
        assertEquals(OfflineMeshGattLinkGate.Stage.TRANSPORT_READY, gate.stage())
    }

    @Test fun closedLinkRejectsLateCallbacksAfterNewSessionBegins() {
        val gate = OfflineMeshGattLinkGate()
        val old = gate.begin()!!
        gate.connected(old, true)
        gate.servicesDiscovered(old, true)
        gate.close()
        val current = gate.begin()!!
        assertFalse(gate.mtuResolved(old, 247, true))
        assertFalse(gate.servicesDiscovered(old, true))
        assertFalse(gate.mayWrite(old, 1))
        assertEquals(OfflineMeshGattLinkGate.Stage.CONNECTING, gate.stage())
        assertTrue(gate.connected(current, true))
    }

}
