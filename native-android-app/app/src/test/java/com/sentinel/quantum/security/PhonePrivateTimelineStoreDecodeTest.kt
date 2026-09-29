package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonePrivateTimelineStoreDecodeTest {
    @Test
    fun missingStoreIsExplicitlyEmpty() {
        val decoded = PhonePrivateTimelineStore.decodeStored(null)
        assertEquals(PhonePrivateTimelineStore.ReadState.EMPTY, decoded.state)
        assertEquals(0, decoded.rejectedEntryCount)
        assertEquals(0, decoded.events.size)
    }

    @Test
    fun validEmptyArrayIsCompleteNotUnreadable() {
        val decoded = PhonePrivateTimelineStore.decodeStored("[]")
        assertEquals(PhonePrivateTimelineStore.ReadState.COMPLETE, decoded.state)
        assertEquals(0, decoded.rejectedEntryCount)
    }

    @Test
    fun malformedJsonIsUnreadable() {
        val decoded = PhonePrivateTimelineStore.decodeStored("{not-an-array}")
        assertEquals(PhonePrivateTimelineStore.ReadState.UNREADABLE, decoded.state)
        assertEquals(1, decoded.rejectedEntryCount)
        assertEquals(0, decoded.events.size)
    }

    @Test
    fun invalidEntryMakesOtherwiseReadableStorePartial() {
        val decoded = PhonePrivateTimelineStore.decodeStored(
            """[
              {"kind":"CALL","timestampMs":1000,"direction":"INCOMING","signal":"INCALL_ACTIVE"},
              {"kind":"NOT_A_KIND","timestampMs":1001,"direction":"LOCAL"}
            ]"""
        )
        assertEquals(PhonePrivateTimelineStore.ReadState.PARTIAL, decoded.state)
        assertEquals(1, decoded.rejectedEntryCount)
        assertEquals(1, decoded.events.size)
    }
}
