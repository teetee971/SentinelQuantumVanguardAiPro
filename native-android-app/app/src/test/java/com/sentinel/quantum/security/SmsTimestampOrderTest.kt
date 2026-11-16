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

    @Test fun aLimitedConversationWindowDoesNotHideNormalMessagesBehindFutureRows() {
        val future = nowMs + 365L * 24L * 60L * 60L * 1000L
        val dates = (1..200).map { future + it } + (1..10).map { nowMs - it }
        var anomalousReads = 0
        val window = SmsTimestampOrder.loadWindow(5,
            loadPlausible = { limit -> dates.filterNot { SmsTimestampOrder.isAnomalous(it, nowMs) }
                .sortedDescending().take(limit) },
            loadAnomalous = { limit -> anomalousReads++; dates.filter { SmsTimestampOrder.isAnomalous(it, nowMs) }
                .sortedDescending().take(limit) }
        )
        assertEquals((1..5).map { nowMs - it }, window)
        assertEquals(0, anomalousReads)
        // The former DATE DESC LIMIT boundary would return only the hostile dates.
        assertEquals(5, dates.sortedDescending().take(5).count { SmsTimestampOrder.isAnomalous(it, nowMs) })
    }

    @Test fun sparseConversationRetainsOriginalAnomalousDatesOnlyInRemainingCapacity() {
        val future = nowMs + SmsTimestampOrder.FUTURE_TOLERANCE_MS + 1L
        val normal = listOf(nowMs, nowMs - 1000L)
        var remainingCapacity = 0
        val window = SmsTimestampOrder.loadWindow(4,
            loadPlausible = { normal.take(it) },
            loadAnomalous = { limit -> remainingCapacity = limit; listOf(future, -1L, -2L).take(limit) }
        )
        assertEquals(2, remainingCapacity)
        assertEquals(listOf(nowMs, nowMs - 1000L, future, -1L), window)
    }
}
