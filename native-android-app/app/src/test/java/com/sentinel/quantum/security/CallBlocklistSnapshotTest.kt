package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class CallBlocklistSnapshotTest {
    @Test
    fun cachedRulesExpireAtTheirDeadlineWithoutReloadingPreferences() {
        val snapshot = CallBlocklistStore.Snapshot(
            blockedNumberHashes = setOf("temporary", "manual"),
            blockedPrefixes = setOf("+33162"),
            signedSilencePrefixes = setOf("+33948"),
            blockedNumberExpiresAtMs = mapOf("temporary" to 2000L),
            signedExpiresAtMs = 3000L
        )
        assertEquals(setOf("temporary", "manual"), snapshot.activeAt(1999L).blockedNumberHashes)
        assertEquals(setOf("manual"), snapshot.activeAt(2000L).blockedNumberHashes)
        assertEquals(setOf("+33948"), snapshot.activeAt(2999L).signedSilencePrefixes)
        assertEquals(emptySet<String>(), snapshot.activeAt(3000L).signedSilencePrefixes)
        assertEquals(setOf("+33162"), snapshot.activeAt(4000L).blockedPrefixes)
        // Filtering a decision must not mutate the shared snapshot.
        assertEquals(setOf("temporary", "manual"), snapshot.blockedNumberHashes)
    }

    @Test
    fun signedRulesWithoutAnExpiryFailClosed() {
        val snapshot = CallBlocklistStore.Snapshot(
            blockedNumberHashes = emptySet(),
            blockedPrefixes = emptySet(),
            signedSilencePrefixes = setOf("+33948")
        )
        assertEquals(emptySet<String>(), snapshot.activeAt(1L).signedSilencePrefixes)
    }
}
