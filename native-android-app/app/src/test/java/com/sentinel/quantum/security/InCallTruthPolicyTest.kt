package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InCallTruthPolicyTest {
    @Test fun legacyDirectionDistinguishesIncomingAndOutgoingSetup() {
        assertEquals("INCOMING", InCallTruthPolicy.legacyDirection(InCallTruthPolicy.LegacyState.RINGING))
        assertEquals("OUTGOING", InCallTruthPolicy.legacyDirection(InCallTruthPolicy.LegacyState.DIALING))
        assertEquals("OUTGOING", InCallTruthPolicy.legacyDirection(InCallTruthPolicy.LegacyState.CONNECTING))
        assertEquals(
            "OUTGOING",
            InCallTruthPolicy.legacyDirection(InCallTruthPolicy.LegacyState.SELECT_PHONE_ACCOUNT)
        )
        assertEquals("UNKNOWN", InCallTruthPolicy.legacyDirection(InCallTruthPolicy.LegacyState.OTHER))
    }

    @Test fun holdRequiresCurrentCapabilityAndRejectsGenericConference() {
        assertTrue(InCallTruthPolicy.canToggleHold(true, false))
        assertFalse(InCallTruthPolicy.canToggleHold(false, false))
        assertFalse(InCallTruthPolicy.canToggleHold(true, true))
    }

    @Test fun muteAvailabilityMatchesCurrentAndroidCapability() {
        assertTrue(InCallTruthPolicy.canMute(true))
        assertFalse(InCallTruthPolicy.canMute(false))
    }
}
