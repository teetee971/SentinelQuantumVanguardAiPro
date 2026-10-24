package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class OfflineMeshBleHostGateTest {
    @Test fun readyOnlyAfterAdvertisingConfirmation() {
        val g = OfflineMeshBleHostGate()
        val id = g.begin()!!
        assertEquals(OfflineMeshBleHostGate.State.REGISTERING, g.state())
        assertTrue(g.serviceRegistered(id, true))
        assertEquals(OfflineMeshBleHostGate.State.STARTING_ADVERTISING, g.state())
        assertTrue(g.advertisingStarted(id, true))
        assertEquals(OfflineMeshBleHostGate.State.ADVERTISING, g.state())
    }

    @Test fun duplicateStartsDenied() {
        val g = OfflineMeshBleHostGate()
        val id = g.begin()
        assertNull(g.begin())
        assertEquals(id, g.generation())
    }

    @Test fun serviceFailureStopsEverything() {
        val g = OfflineMeshBleHostGate()
        val id = g.begin()!!
        assertFalse(g.serviceRegistered(id, false))
        assertEquals(OfflineMeshBleHostGate.State.STOPPED, g.state())
        assertFalse(g.advertisingStarted(id, true))
    }

    @Test fun advertisingFailureRevokesState() {
        val g = OfflineMeshBleHostGate()
        val id = g.begin()!!
        g.serviceRegistered(id, true)
        assertFalse(g.advertisingStarted(id, false))
        assertEquals(OfflineMeshBleHostGate.State.STOPPED, g.state())
    }

    @Test fun staleCallbacksCannotRestartHost() {
        val g = OfflineMeshBleHostGate()
        val old = g.begin()!!
        g.stop()
        val current = g.begin()!!
        assertFalse(g.serviceRegistered(old, true))
        assertTrue(g.serviceRegistered(current, true))
        assertFalse(g.advertisingStarted(old, true))
        assertEquals(OfflineMeshBleHostGate.State.STARTING_ADVERTISING, g.state())
    }

    @Test fun stopDuringAdvertisingPendingRejectsLateSuccess() {
        val g = OfflineMeshBleHostGate()
        val id = g.begin()!!
        g.serviceRegistered(id, true)
        g.stop()
        assertFalse(g.advertisingStarted(id, true))
        assertEquals(OfflineMeshBleHostGate.State.STOPPED, g.state())
    }
}
