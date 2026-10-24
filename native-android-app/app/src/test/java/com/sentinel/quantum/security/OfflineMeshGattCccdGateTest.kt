package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class OfflineMeshGattCccdGateTest {
    @Test fun successfulAckRequiresPendingWriteAndMatchingEpoch() {
        val gate = OfflineMeshGattCccdGate()
        assertTrue(gate.begin(1))
        assertFalse(gate.completed(1, true, true))
        assertTrue(gate.started(1))
        assertFalse(gate.completed(0, true, true))
        assertFalse(gate.completed(1, false, true))
        assertEquals(OfflineMeshGattCccdGate.State.PENDING, gate.state())
        assertTrue(gate.completed(1, true, true))
        assertEquals(OfflineMeshGattCccdGate.State.SUBSCRIBED, gate.state())
        assertFalse(gate.privateDataAuthorized())
    }

    @Test fun restartAndCloseRejectStaleCallbacks() {
        val gate = OfflineMeshGattCccdGate()
        assertTrue(gate.begin(1))
        assertTrue(gate.started(1))
        gate.clear()
        assertFalse(gate.completed(1, true, true))
        assertTrue(gate.begin(2))
        assertFalse(gate.started(1))
        assertFalse(gate.completed(1, true, true))
        assertTrue(gate.started(2))
        assertFalse(gate.completed(2, true, false))
        assertEquals(OfflineMeshGattCccdGate.State.PENDING, gate.state())
        assertFalse(gate.privateDataAuthorized())
    }

    @Test fun duplicateOrDescendingEpochCannotReopenGate() {
        val gate = OfflineMeshGattCccdGate()
        assertTrue(gate.begin(5))
        assertTrue(gate.started(5))
        assertFalse(gate.begin(5))
        assertFalse(gate.begin(4))
        assertEquals(OfflineMeshGattCccdGate.State.PENDING, gate.state())
    }
}
