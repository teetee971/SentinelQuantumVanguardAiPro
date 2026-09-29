package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelCleanupPlanTest {
    private fun item(
        id: String,
        scope: SentinelCleanupPolicy.Scope,
        bytes: Long?,
        state: SentinelCleanupPolicy.ActionState
    ) = SentinelCleanupPolicy.Candidate(id, scope, id, bytes, state)

    @Test
    fun unknownSizesAreNotInventedIntoTotal() {
        val summary = SentinelCleanupPlan.summarize(
            listOf(
                item("known", SentinelCleanupPolicy.Scope.SENTINEL_CACHE, 100L, SentinelCleanupPolicy.ActionState.EXECUTABLE),
                item("unknown", SentinelCleanupPolicy.Scope.SENTINEL_CACHE, null, SentinelCleanupPolicy.ActionState.EXECUTABLE)
            )
        )
        assertEquals(100L, summary.knownReclaimableBytes)
        assertEquals(1, summary.unknownSizeCount)
        assertFalse(summary.hasCompleteSizeEstimate)
    }

    @Test
    fun verifiedRemovedItemsAreExcludedFromReclaimablePlan() {
        val summary = SentinelCleanupPlan.summarize(
            listOf(
                item("gone", SentinelCleanupPolicy.Scope.SENTINEL_CACHE, 0L, SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED),
                item("live", SentinelCleanupPolicy.Scope.SENTINEL_CACHE, 50L, SentinelCleanupPolicy.ActionState.EXECUTABLE)
            )
        )
        assertEquals(1, summary.candidateCount)
        assertEquals(50L, summary.knownReclaimableBytes)
        assertTrue(summary.hasCompleteSizeEstimate)
    }

    @Test
    fun inaccessibleExternalDataIsCountedButNotExecutable() {
        val summary = SentinelCleanupPlan.summarize(
            listOf(
                item("external", SentinelCleanupPolicy.Scope.EXTERNAL_APP_PRIVATE_DATA, null, SentinelCleanupPolicy.ActionState.NOT_ACCESSIBLE)
            )
        )
        assertEquals(0, summary.directlyExecutableCount)
        assertEquals(1, summary.inaccessibleCount)
    }
}
