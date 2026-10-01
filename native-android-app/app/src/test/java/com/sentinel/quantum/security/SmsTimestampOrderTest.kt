package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsTimestampOrderTest {
    private val nowMs = 1_000_000L

    @Test fun preservesPastAndSmallFutureSkew() {
        assertEquals(nowMs - 1_000L, SmsTimestampOrder.sortTimestamp(nowMs - 1_000L, nowMs))
        assertEquals(nowMs + 60_000L, SmsTimestampOrder.sortTimestamp(nowMs + 60_000L, nowMs))
    }

    @Test fun placesImplausibleFutureTimestampAfterNormalDates() {
        val original = nowMs + SmsTimestampOrder.FUTURE_TOLERANCE_MS + 1L
        assertEquals(Long.MIN_VALUE, SmsTimestampOrder.sortTimestamp(original, nowMs))
        assertEquals(nowMs + 30_000L, SmsTimestampOrder.sortTimestamp(nowMs + 30_000L, nowMs))
    }

    @Test fun isDeterministicForFixedClock() {
        val original = nowMs + 60L * 60L * 1000L
        repeat(10) {
            assertEquals(Long.MIN_VALUE, SmsTimestampOrder.sortTimestamp(original, nowMs))
        }
    }
    @Test fun futureMessageCannotHideTheRealLatestMessage() {
        val future = nowMs + 365L * 24L * 60L * 60L * 1000L
        val recent = nowMs - 1_000L
        assertEquals(recent, listOf(future, recent).maxBy { SmsTimestampOrder.sortTimestamp(it, nowMs) })
    }

    @Test fun classifiesBoundaryNegativeDatesAndLongOverflow() {
        assertEquals(false, SmsTimestampOrder.isAnomalous(nowMs + SmsTimestampOrder.FUTURE_TOLERANCE_MS, nowMs))
        assertEquals(true, SmsTimestampOrder.isAnomalous(nowMs + SmsTimestampOrder.FUTURE_TOLERANCE_MS + 1L, nowMs))
        assertEquals(true, SmsTimestampOrder.isAnomalous(-1L, nowMs))
        assertEquals(false, SmsTimestampOrder.isAnomalous(Long.MAX_VALUE, Long.MAX_VALUE - 1L))
        assertEquals(Long.MIN_VALUE, SmsTimestampOrder.sortTimestamp(-1L, nowMs))
    }
}
