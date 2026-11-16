package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallTimelineMapperTest {
    @Test fun screeningEvidenceIsExplicitAndNeverStoresTheNumber() {
        val rawNumber = "+33123456789"
        val event = CallTimelineMapper.toEvent(
            CallRuleEngine.Decision(
                action = CallRuleEngine.Action.BLOCK,
                reason = "USER_EXACT_BLOCK",
                normalizedNumber = rawNumber,
                source = CallRuleEngine.RuleSource.USER
            ),
            timestampMs = 1234L
        )
        assertEquals(PhonePrivateTimeline.Kind.CALL, event.kind)
        assertEquals("INCOMING", event.direction)
        assertEquals(1234L, event.timestampMs)
        assertTrue(event.signal.orEmpty().startsWith(PhoneCorePhysicalValidation.SIGNAL_CALL_SCREENED_PREFIX))
        assertFalse(event.signal.orEmpty().contains(rawNumber))
    }
}
