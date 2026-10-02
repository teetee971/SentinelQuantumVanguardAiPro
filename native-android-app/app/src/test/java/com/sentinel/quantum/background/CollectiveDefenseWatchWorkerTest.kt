package com.sentinel.quantum.background

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectiveDefenseWatchWorkerTest {
    @Test
    fun riskRankingIsMonotonicAndUnknownFailsOpen() {
        assertEquals(0, CollectiveDefenseWatchWorker.riskRank("UNKNOWN"))
        assertEquals(0, CollectiveDefenseWatchWorker.riskRank("unexpected"))
        assertEquals(1, CollectiveDefenseWatchWorker.riskRank("OBSERVED"))
        assertEquals(2, CollectiveDefenseWatchWorker.riskRank("SUSPICIOUS"))
        assertEquals(3, CollectiveDefenseWatchWorker.riskRank("HIGH_CONFIDENCE"))
    }

    @Test
    fun unsupportedRefreshIntervalsDisableBackgroundWatch() {
        assertEquals(
            CollectiveDefensePreferences.INTERVAL_NEVER,
            CollectiveDefensePreferences.sanitizeInterval(-1)
        )
        assertEquals(
            CollectiveDefensePreferences.INTERVAL_NEVER,
            CollectiveDefensePreferences.sanitizeInterval(4)
        )
        assertEquals(12, CollectiveDefensePreferences.sanitizeInterval(12))
        assertEquals(24, CollectiveDefensePreferences.sanitizeInterval(24))
    }
}
