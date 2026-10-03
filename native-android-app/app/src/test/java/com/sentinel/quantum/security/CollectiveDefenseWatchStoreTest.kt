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
            lastAttemptedAtMs = 2500L,
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
    fun legacySevenFieldRecordMigratesAttemptTimeFromLastSuccessfulCheck() {
        val legacy = listOf(
            "EMAIL",
            "d".repeat(64),
            "1000",
            "2000",
            "OBSERVED",
            "1",
            "available"
        ).joinToString("|")

        val decoded = CollectiveDefenseWatchStore.decode(legacy)
        assertEquals(2000L, decoded?.lastCheckedAtMs)
        assertEquals(2000L, decoded?.lastAttemptedAtMs)
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
    @Test fun watchReputationExpiresWithoutDeletingTheWatchedFingerprint() {
        val item = CollectiveDefenseWatchStore.WatchItem(
            CollectiveDefenseClient.IndicatorType.EMAIL, "a".repeat(64), 1000L, 2000L, 2000L,
            "SUSPICIOUS", 3, "available", reputationExpiresAtMs = 3000L
        )
        assertEquals("SUSPICIOUS", item.activeRiskState(2999L))
        assertEquals("UNKNOWN", item.activeRiskState(3000L))
        assertEquals(item, CollectiveDefenseWatchStore.decode(CollectiveDefenseWatchStore.encode(item)))
        assertEquals("UNKNOWN", item.copy(reputationExpiresAtMs = null).activeRiskState(2999L))
    }
    @Test fun storingAnOldResponseCannotRenewItsRemainingTtl() {
        val response = CollectiveDefenseClient.ReputationResult(
            CollectiveDefenseClient.IndicatorType.EMAIL, "a".repeat(64), "SUSPICIOUS", 3,
            emptyList(), "available", 900L, 1000L, false, "", receivedAtMs = 1000L
        )
        assertEquals(2000L, CollectiveDefenseWatchStore.reputationDeadline(response, 1500L))
        assertNull(CollectiveDefenseWatchStore.reputationDeadline(response, 2000L))
        assertNull(CollectiveDefenseWatchStore.reputationDeadline(response, 999L))
    }
}
