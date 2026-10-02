package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectiveDefenseWatchStoreTest {
    @Test
    fun codecStoresOnlyOpaqueFingerprintAndBoundedMetadata() {
        val rawIndicator = "fraud@example.com"
        val item = CollectiveDefenseWatchStore.WatchItem(
            indicatorType = CollectiveDefenseClient.IndicatorType.EMAIL,
            fingerprint = "c".repeat(64),
            addedAtMs = 1000L,
            lastCheckedAtMs = 2000L,
            riskState = "SUSPICIOUS",
            signals = 3,
            communityIntelligence = "available"
        )

        val encoded = CollectiveDefenseWatchStore.encode(item)
        assertFalse(encoded.contains(rawIndicator))
        assertTrue(encoded.contains("c".repeat(64)))

        val decoded = CollectiveDefenseWatchStore.decode(encoded)
        assertEquals(item, decoded)
    }

    @Test
    fun codecRejectsMalformedOrUnboundedRecords() {
        assertNull(CollectiveDefenseWatchStore.decode("EMAIL|bad|1|2|UNKNOWN|0|available"))
        assertNull(
            CollectiveDefenseWatchStore.decode(
                "EMAIL|" + "a".repeat(64) + "|10|5|UNKNOWN|0|available"
            )
        )
        assertNull(
            CollectiveDefenseWatchStore.decode(
                "EMAIL|" + "a".repeat(64) + "|1|2|not valid token|0|available"
            )
        )
    }
}
