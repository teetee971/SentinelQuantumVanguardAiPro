package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CallHistoryPrivacyTest {
    @Test fun boundsCountsAndRejectsInvalidTimestamp() {
        val result = CallHistoryPrivacy.boundedContext(999, -4, -1)
        assertEquals(CallHistoryPrivacy.MAX_RECENT_EVENTS, result.recentCalls)
        assertEquals(0, result.missedCalls)
        assertNull(result.lastInteractionEpochMillis)
    }

    @Test fun localContextIsNeverRemotePayload() {
        assertFalse(CallHistoryPrivacy.permitsRemoteTransmission())
    }
}
