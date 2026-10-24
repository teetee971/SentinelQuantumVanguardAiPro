package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class OfflineMeshBleSessionTest {
    private fun ready() = OfflineMeshBleReadiness.Result(OfflineMeshBleReadiness.Status.READY, "OK")
    @Test fun privateSendRequiresAuthenticatedProof() {
        val s = OfflineMeshBleSession()
        assertFalse(s.canSendPrivateMessages())
        s.prepare(ready())
        val generation = s.start()!!
        assertFalse(s.canSendPrivateMessages())
        assertTrue(s.linkConnected(generation))
        assertFalse(s.canSendPrivateMessages())
        assertFalse(s.authenticated(generation, false))
        assertFalse(s.canSendPrivateMessages())
        assertTrue(s.authenticated(generation, true))
        assertTrue(s.canSendPrivateMessages())
    }
    @Test fun staleCallbackCannotReopenSessionAfterStop() {
        val s = OfflineMeshBleSession()
        s.prepare(ready())
        val previous = s.start()!!
        s.stop()
        s.prepare(ready())
        s.start()
        assertFalse(s.linkConnected(previous))
        assertFalse(s.canSendPrivateMessages())
    }
    @Test fun radioLossRevokesAuthentication() {
        val s = OfflineMeshBleSession()
        s.prepare(ready())
        val g = s.start()!!
        s.linkConnected(g)
        s.authenticated(g, true)
        s.radioLost()
        assertEquals(OfflineMeshBleSession.State.LIMITED, s.state())
        assertFalse(s.canSendPrivateMessages())
        assertFalse(s.authenticated(g, true))
    }
    @Test fun lockedReadinessCannotStartRadio() {
        val s = OfflineMeshBleSession()
        s.prepare(OfflineMeshBleReadiness.Result(OfflineMeshBleReadiness.Status.LOCKED, "MISSING_PERMISSION"))
        assertNull(s.start())
        assertEquals(OfflineMeshBleSession.State.LOCKED, s.state())
    }
}
