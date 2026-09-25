package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsTimestampOrderTest {
    private val nowMs = 1_000_000L

    @Test fun preservesPastAndSmallFutureSkew() {
        assertEquals(nowMs - 1_000L, SmsTimestampOrder.sortTimestamp(nowMs - 1_000L, nowMs))
        assertEquals(nowMs + 60_000L, SmsTimestampOrder.sortTimestamp(nowMs + 60_000L, nowMs))
    }

    @Test fun boundsImplausibleFutureTimestampForOrderingOnly() {
        val original = nowMs + SmsTimestampOrder.FUTURE_TOLERANCE_MS + 1L
        assertEquals(nowMs, SmsTimestampOrder.sortTimestamp(original, nowMs))
        assertEquals(nowMs + 30_000L, SmsTimestampOrder.sortTimestamp(nowMs + 30_000L, nowMs))
    }

    @Test fun isDeterministicForFixedClock() {
        val original = nowMs + 60L * 60L * 1000L
        repeat(10) {
            assertEquals(nowMs, SmsTimestampOrder.sortTimestamp(original, nowMs))
        }
    }
}
