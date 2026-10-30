package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class CallBlockingUiSnapshotTest {
    private val empty = CallBlocklistStore.Snapshot(emptySet(), emptySet(), emptySet())
    private val blocked = empty.copy(blockedNumberHashes = setOf("a".repeat(64)))

    @Test fun delayedResumeReadCannotHideNewlyAddedRule() {
        val initial = CallBlockingUiSnapshot(empty)
        val readRevision = initial.revision
        val added = initial.afterMutation(blocked)
        val displayed = added.afterRefresh(readRevision, empty)
        assertEquals(setOf("a".repeat(64)), displayed.snapshot.blockedNumberHashes)
        assertEquals(added.revision, displayed.revision)
    }

    @Test fun delayedResumeReadCannotRestoreClearedRules() {
        val initial = CallBlockingUiSnapshot(blocked)
        val readRevision = initial.revision
        val cleared = initial.afterMutation(empty)
        assertTrue(cleared.afterRefresh(readRevision, blocked).snapshot.blockedNumberHashes.isEmpty())
    }

    @Test fun unmodifiedScreenAcceptsRefreshedRules() {
        val initial = CallBlockingUiSnapshot(empty)
        val displayed = initial.afterRefresh(initial.revision, blocked)
        assertEquals(setOf("a".repeat(64)), displayed.snapshot.blockedNumberHashes)
        assertEquals(initial.revision, displayed.revision)
    }
}
