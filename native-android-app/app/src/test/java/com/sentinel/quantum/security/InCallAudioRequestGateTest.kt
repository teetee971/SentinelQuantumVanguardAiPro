package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class InCallAudioRequestGateTest {
    @Test fun newerRouteRequestRejectsOldCompletion() {
        val gate = InCallAudioRequestGate()
        val old = gate.begin("call-a")
        val latest = gate.begin("call-a")
        assertFalse(gate.accepts(old, "call-a"))
        assertTrue(gate.accepts(latest, "call-a"))
    }
    @Test fun completionCannotModifyAnotherCall() {
        val gate = InCallAudioRequestGate()
        val token = gate.begin("call-a")
        assertFalse(gate.accepts(token, "call-b"))
        assertFalse(gate.accepts(token, null))
    }
    @Test fun teardownInvalidatesEvenTheSameCall() {
        val gate = InCallAudioRequestGate()
        val token = gate.begin("call-a")
        gate.invalidate()
        assertFalse(gate.accepts(token, "call-a"))
    }
}
