package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelCleanupBatchTest {
    private fun candidate(
        id: String,
        bytes: Long?,
        state: SentinelCleanupPolicy.ActionState
    ) = SentinelCleanupPolicy.Candidate(
        stableId = id,
        scope = SentinelCleanupPolicy.Scope.SENTINEL_CACHE,
        displayName = id,
        bytes = bytes,
        state = state
    )

    @Test
    fun partialSuccessNeverBecomesFullyVerified() {
        val removed = SentinelCleanupPolicy.Result(
            candidate("a", 100L, SentinelCleanupPolicy.ActionState.EXECUTABLE),
            candidate("a", 0L, SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED)
        )
        val failed = SentinelCleanupPolicy.Result(
            candidate("b", 50L, SentinelCleanupPolicy.ActionState.EXECUTABLE),
            candidate("b", 50L, SentinelCleanupPolicy.ActionState.FAILED)
        )
        val summary = SentinelCleanupBatch.summarize(listOf(removed, failed))
        assertEquals(1, summary.verifiedRemovedCount)
        assertEquals(1, summary.failedCount)
        assertEquals(100L, summary.knownVerifiedFreedBytes)
        assertFalse(summary.isFullyVerified)
    }

    @Test
    fun allVerifiedResultsCanBeFullyVerified() {
        val result = SentinelCleanupPolicy.Result(
            candidate("a", 100L, SentinelCleanupPolicy.ActionState.EXECUTABLE),
            candidate("a", 0L, SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED)
        )
        val summary = SentinelCleanupBatch.summarize(listOf(result))
        assertTrue(summary.isFullyVerified)
        assertEquals(100L, summary.knownVerifiedFreedBytes)
    }

    @Test
    fun emptyBatchIsNotReportedAsFullyVerified() {
        assertFalse(SentinelCleanupBatch.summarize(emptyList()).isFullyVerified)
    }
}
