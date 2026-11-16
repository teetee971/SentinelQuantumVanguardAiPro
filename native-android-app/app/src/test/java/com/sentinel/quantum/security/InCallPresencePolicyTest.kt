package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class InCallPresencePolicyTest {
    @Test fun realSystemCallWithoutSentinelSessionIsNeverIdle() {
        for (hadSession in listOf(true, false)) for (waiting in listOf(true, false))
            assertEquals(InCallPresencePolicy.MissingSession.CALL_UNAVAILABLE,
                InCallPresencePolicy.resolve(true, hadSession, waiting))
    }
    @Test fun unknownPlatformStateIsNeverReportedAsNoCallOrEnded() {
        assertEquals(InCallPresencePolicy.MissingSession.UNKNOWN,
            InCallPresencePolicy.resolve(null, true, false))
        assertEquals(InCallPresencePolicy.MissingSession.UNKNOWN,
            InCallPresencePolicy.resolve(null, false, false))
    }
    @Test fun initialBindingHasABoundedConnectingState() {
        assertEquals(InCallPresencePolicy.MissingSession.CONNECTING,
            InCallPresencePolicy.resolve(false, false, true))
        assertEquals(InCallPresencePolicy.MissingSession.IDLE,
            InCallPresencePolicy.resolve(false, false, false))
    }
    @Test fun finishedStateNeedsBothPreviousSessionAndPlatformConfirmation() {
        assertEquals(InCallPresencePolicy.MissingSession.ENDED,
            InCallPresencePolicy.resolve(false, true, false))
    }
}
