package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class InCallDirectionPolicyTest {
    @Test fun incomingDirectionSurvivesAnswerAndLaterDetailsUpdates() {
        var direction = InCallDirectionPolicy.reconcile(null, "UNKNOWN")
        assertEquals("UNKNOWN", direction)
        direction = InCallDirectionPolicy.reconcile(direction, "INCOMING")
        assertEquals("INCOMING", direction)
        direction = InCallDirectionPolicy.reconcile(direction, "OUTGOING")
        assertEquals("INCOMING", direction)
        assertEquals("INCOMING", InCallDirectionPolicy.reconcile(direction, "UNKNOWN"))
    }

    @Test fun independentCallsRetainTheirOwnDirection() {
        val outgoing = InCallDirectionPolicy.reconcile(null, "OUTGOING")
        val incoming = InCallDirectionPolicy.reconcile(null, "INCOMING")
        assertEquals("OUTGOING", InCallDirectionPolicy.reconcile(outgoing, "UNKNOWN"))
        assertEquals("INCOMING", InCallDirectionPolicy.reconcile(incoming, "UNKNOWN"))
        assertEquals("UNKNOWN", InCallDirectionPolicy.reconcile(null, "INVALID"))
    }
}
