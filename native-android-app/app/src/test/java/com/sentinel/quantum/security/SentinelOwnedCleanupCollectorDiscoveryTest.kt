package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelOwnedCleanupCollectorDiscoveryTest {
    @Test
    fun zeroObservedRootsCannotBeReportedComplete() {
        val discovery = SentinelOwnedCleanupCollector.Discovery(
            candidates = emptyList(),
            rootCount = 0,
            unreadableRootCount = 0
        )
        assertFalse(discovery.isComplete)
    }

    @Test
    fun unreadableRootMakesDiscoveryPartial() {
        val discovery = SentinelOwnedCleanupCollector.Discovery(
            candidates = emptyList(),
            rootCount = 2,
            unreadableRootCount = 1
        )
        assertFalse(discovery.isComplete)
    }

    @Test
    fun observedRootsWithoutFailuresAreComplete() {
        val discovery = SentinelOwnedCleanupCollector.Discovery(
            candidates = emptyList(),
            rootCount = 2,
            unreadableRootCount = 0
        )
        assertTrue(discovery.isComplete)
    }
}
