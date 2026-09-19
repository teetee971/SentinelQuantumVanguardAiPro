package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallHistoryPresentationMapperTest {
    @Test fun projectionDoesNotExposeFingerprintValue() {
        val secret = "device-bound-secret-fingerprint"
        val model = CallHistoryPresentationMapper.from(CallFilterDecisionEntity(id=9, occurredAtMs=1000L, action="BLOCK", reason="USER_EXACT_BLOCK", source="USER", numberFingerprint=secret))
        assertTrue(model.hasPrivateIdentifier)
        assertFalse(model.toString().contains(secret))
        assertEquals("USER_EXACT_BLOCK", model.reason)
    }

    @Test fun presentationTokensAreSanitized() {
        val model = CallHistoryPresentationMapper.from(CallFilterDecisionEntity(occurredAtMs=1000L, action="ALLOW", reason="bad|reason\nraw", source="NONE|raw", numberFingerprint=null))
        assertEquals("badreasonraw", model.reason)
        assertEquals("NONEraw", model.source)
        assertFalse(model.hasPrivateIdentifier)
    }
}
