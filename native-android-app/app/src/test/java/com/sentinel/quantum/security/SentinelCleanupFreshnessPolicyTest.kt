package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelCleanupFreshnessPolicyTest {
    private fun observation(bytes: Long?, modified: Long?) =
        SentinelCleanupFreshnessPolicy.Observation(bytes, modified)

    @Test
    fun unchangedObservationIsFresh() {
        assertTrue(SentinelCleanupFreshnessPolicy.isFresh(observation(100L, 10L), observation(100L, 10L)))
    }

    @Test
    fun changedSizeIsStaleEvenWhenTimestampIsPreserved() {
        assertFalse(SentinelCleanupFreshnessPolicy.isFresh(observation(100L, 10L), observation(101L, 10L)))
    }

    @Test
    fun changedTimestampIsStaleEvenWhenSizeIsPreserved() {
        assertFalse(SentinelCleanupFreshnessPolicy.isFresh(observation(100L, 10L), observation(100L, 11L)))
    }

    @Test
    fun unknownDiscoveryFieldsDoNotInventEvidence() {
        assertTrue(SentinelCleanupFreshnessPolicy.isFresh(observation(null, null), observation(100L, 10L)))
    }
}
