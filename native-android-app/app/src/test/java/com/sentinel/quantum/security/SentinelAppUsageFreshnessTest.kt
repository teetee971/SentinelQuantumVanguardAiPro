package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelAppUsageFreshnessTest {
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun sevenDayEvidenceIsStillFresh() {
        assertEquals(
            SentinelAppUsageFreshness.State.FRESH,
            SentinelAppUsageFreshness.evaluate(0L, 7L * day)
        )
    }

    @Test
    fun olderEvidenceIsStale() {
        assertEquals(
            SentinelAppUsageFreshness.State.STALE,
            SentinelAppUsageFreshness.evaluate(0L, 8L * day)
        )
    }

    @Test
    fun futureEvidenceIsInvalid() {
        assertEquals(
            SentinelAppUsageFreshness.State.INVALID,
            SentinelAppUsageFreshness.evaluate(2L * day, day)
        )
    }
}
