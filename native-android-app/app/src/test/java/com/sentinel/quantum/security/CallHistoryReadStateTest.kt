package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class CallHistoryReadStateTest {
    private val entry = SystemCallLogReader.Entry("0123456789", 1, 1000, 10, "Fixture")

    @Test fun accessIsDeniedUntilBothRoleAndPermissionArePresent() {
        for ((role, permission) in listOf(false to false, false to true, true to false)) {
            val state = CallHistoryReadState().withAccess(role, permission).beginRead()
            assertFalse(state.allowed)
            assertFalse(state.loading)
            assertTrue(state.complete(state.generation, listOf(entry), true).entries.isEmpty())
        }
    }

    @Test fun revocationImmediatelyDropsPreviouslyVisibleRows() {
        val loading = CallHistoryReadState().withAccess(true, true).beginRead()
        val loaded = loading.complete(loading.generation, listOf(entry), true)
        assertEquals(listOf(entry), loaded.entries)
        for ((role, permission) in listOf(false to true, true to false)) {
            val denied = loaded.withAccess(role, permission)
            assertFalse(denied.allowed)
            assertFalse(denied.loading)
            assertTrue(denied.entries.isEmpty())
            assertTrue(denied.withAccess(true, true).entries.isEmpty())
        }
    }

    @Test fun resultReadBeforeRevocationCannotReturnAfterRegrant() {
        val oldRead = CallHistoryReadState().withAccess(true, true).beginRead()
        val newRead = oldRead.withAccess(false, true).withAccess(true, true).beginRead()
        val staleCompletion = newRead.complete(oldRead.generation, listOf(entry), true)
        assertTrue(staleCompletion.entries.isEmpty())
        assertTrue(staleCompletion.loading)
        val replacement = entry.copy(dateMillis = 2000)
        assertEquals(listOf(replacement), staleCompletion.complete(newRead.generation, listOf(replacement), true).entries)
    }

    @Test fun permissionLossAtCompletionRejectsRowsEvenBeforeObserverRuns() {
        val loading = CallHistoryReadState().withAccess(true, true).beginRead()
        val denied = loading.complete(loading.generation, listOf(entry), false)
        assertFalse(denied.allowed)
        assertFalse(denied.loading)
        assertTrue(denied.entries.isEmpty())
    }

    @Test fun repeatedRefreshDoesNotInvalidateAnAlreadyRunningRead() {
        val loading = CallHistoryReadState().withAccess(true, true).beginRead()
        val repeated = loading.withAccess(true, true).beginRead()
        assertEquals(loading.generation, repeated.generation)
        assertEquals(listOf(entry), repeated.complete(loading.generation, listOf(entry), true).entries)
    }

    @Test fun leavingForegroundInvalidatesReadEvenWhenRoleAndPermissionReturnUnchanged() {
        val oldRead = CallHistoryReadState().withAccess(true, true).beginRead()
        val paused = oldRead.withAccess(true, true, resumed = false)
        assertFalse(paused.allowed)
        assertFalse(paused.loading)
        val resumed = paused.withAccess(true, true, resumed = true).beginRead()
        val stale = resumed.complete(oldRead.generation, listOf(entry), true)
        assertTrue(stale.entries.isEmpty())
        assertTrue(stale.loading)
        assertEquals(listOf(entry), stale.complete(resumed.generation, listOf(entry), true).entries)
    }

    @Test fun loadedRowsAreAnOwnedSnapshotAndDuplicateCallbacksAreIgnored() {
        val loading = CallHistoryReadState().withAccess(true, true).beginRead()
        val source = mutableListOf(entry)
        val loaded = loading.complete(loading.generation, source, true)
        source.clear()
        assertEquals(listOf(entry), loaded.entries)
        assertEquals(listOf(entry), loaded.complete(loading.generation, emptyList(), true).entries)
    }
}
