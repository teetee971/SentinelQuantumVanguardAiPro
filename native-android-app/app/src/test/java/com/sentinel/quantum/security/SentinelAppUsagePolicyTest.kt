package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelAppUsagePolicyTest {
    private val day = 24L * 60L * 60L * 1000L
    private val now = 200L * day

    private fun snapshot(
        access: Boolean = true,
        lastUsed: Long? = 190L * day,
        started: Long? = 0L
    ) = SentinelAppUsagePolicy.Snapshot(
        packageName = "example.app",
        usageAccessAvailable = access,
        lastUsedEpochMillis = lastUsed,
        observationStartedEpochMillis = started,
        observedAtEpochMillis = now
    )

    @Test
    fun missingUsageAccessNeverMeansUnused() {
        assertEquals(
            SentinelAppUsagePolicy.Observation.NOT_OBSERVABLE,
            SentinelAppUsagePolicy.evaluate(snapshot(access = false, lastUsed = null))
        )
    }

    @Test
    fun oldObservedUsageCanBeClassifiedStale() {
        assertEquals(
            SentinelAppUsagePolicy.Observation.STALE_USAGE_CONFIRMED,
            SentinelAppUsagePolicy.evaluate(snapshot(lastUsed = 50L * day))
        )
    }

    @Test
    fun recentObservedUsageIsRecent() {
        assertEquals(
            SentinelAppUsagePolicy.Observation.RECENTLY_USED,
            SentinelAppUsagePolicy.evaluate(snapshot(lastUsed = 190L * day))
        )
    }

    @Test
    fun noUsageNeedsLongEnoughObservationWindow() {
        assertEquals(
            SentinelAppUsagePolicy.Observation.NOT_OBSERVABLE,
            SentinelAppUsagePolicy.evaluate(snapshot(lastUsed = null, started = 150L * day))
        )
        assertEquals(
            SentinelAppUsagePolicy.Observation.NEVER_USED_SINCE_OBSERVATION_START,
            SentinelAppUsagePolicy.evaluate(snapshot(lastUsed = null, started = 0L))
        )
    }

    @Test
    fun impossibleTimestampsRemainInvalid() {
        assertEquals(
            SentinelAppUsagePolicy.Observation.INVALID,
            SentinelAppUsagePolicy.evaluate(snapshot(lastUsed = now + day))
        )
    }
}
