package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelCleanupPolicyTest {
    private fun candidate(
        scope: SentinelCleanupPolicy.Scope,
        state: SentinelCleanupPolicy.ActionState,
        bytes: Long? = 100L
    ) = SentinelCleanupPolicy.Candidate("id", scope, "item", bytes, state)

    @Test
    fun offeringCleanupNeverCountsAsCleaned() {
        val before = candidate(SentinelCleanupPolicy.Scope.SENTINEL_CACHE, SentinelCleanupPolicy.ActionState.EXECUTABLE)
        val after = candidate(SentinelCleanupPolicy.Scope.SENTINEL_CACHE, SentinelCleanupPolicy.ActionState.EXECUTED_UNVERIFIED)
        val result = SentinelCleanupPolicy.Result(before, after)
        assertFalse(result.isVerifiedCleaned)
        assertNull(result.verifiedFreedBytes)
    }

    @Test
    fun onlyVerifiedRemovalProducesFreedBytes() {
        val before = candidate(SentinelCleanupPolicy.Scope.SENTINEL_CACHE, SentinelCleanupPolicy.ActionState.EXECUTABLE, 500L)
        val after = candidate(SentinelCleanupPolicy.Scope.SENTINEL_CACHE, SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED, 0L)
        val result = SentinelCleanupPolicy.Result(before, after)
        assertTrue(result.isVerifiedCleaned)
        assertEquals(500L, result.verifiedFreedBytes)
    }

    @Test
    fun externalAppPrivateDataIsNeverDirectlyCleanable() {
        val item = candidate(SentinelCleanupPolicy.Scope.EXTERNAL_APP_PRIVATE_DATA, SentinelCleanupPolicy.ActionState.NOT_ACCESSIBLE)
        assertFalse(SentinelCleanupPolicy.canExecuteDirectly(item))
        assertTrue(SentinelCleanupPolicy.isUnsupportedDirectCleanup(item))
    }

    @Test
    fun userSelectedFilesRequireUserSelection() {
        val item = candidate(SentinelCleanupPolicy.Scope.USER_SELECTED_FILE, SentinelCleanupPolicy.ActionState.USER_CONFIRMATION_REQUIRED)
        assertTrue(SentinelCleanupPolicy.requiresUserSelection(item))
        assertFalse(SentinelCleanupPolicy.canExecuteDirectly(item))
    }
    @Test
    fun alreadyAbsentDoesNotClaimPreviouslyObservedBytesAsFreed() {
        val before = SentinelCleanupPolicy.Candidate(
            stableId = "cache/a",
            scope = SentinelCleanupPolicy.Scope.SENTINEL_CACHE,
            displayName = "a",
            bytes = 500L,
            state = SentinelCleanupPolicy.ActionState.EXECUTABLE
        )
        val after = before.copy(bytes = 0L, state = SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED)
        val result = SentinelCleanupPolicy.Result(
            before,
            after,
            SentinelCleanupPolicy.ExecutionEffect.ALREADY_ABSENT
        )
        assertEquals(0L, result.verifiedFreedBytes)
    }

    @Test
    fun directExecutionRequiresExecutableState() {
        val blockedStates = listOf(
            SentinelCleanupPolicy.ActionState.DISCOVERED,
            SentinelCleanupPolicy.ActionState.USER_CONFIRMATION_REQUIRED,
            SentinelCleanupPolicy.ActionState.EXECUTED_UNVERIFIED,
            SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED,
            SentinelCleanupPolicy.ActionState.NOT_ACCESSIBLE,
            SentinelCleanupPolicy.ActionState.FAILED
        )
        blockedStates.forEach { state ->
            val candidate = SentinelCleanupPolicy.Candidate(
                stableId = "cache/a",
                scope = SentinelCleanupPolicy.Scope.SENTINEL_CACHE,
                displayName = "a",
                bytes = 1L,
                state = state
            )
            assertFalse(SentinelCleanupPolicy.canExecuteDirectly(candidate))
        }
    }

}
