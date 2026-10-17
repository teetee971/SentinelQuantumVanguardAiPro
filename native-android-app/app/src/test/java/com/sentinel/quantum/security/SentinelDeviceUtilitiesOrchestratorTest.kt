package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelDeviceUtilitiesOrchestratorTest {
    @Test
    fun discoveryBuildsPlanFromCollectorWithoutExecutingAnything() {
        var executions = 0
        val candidate = candidate("/tmp/cache-a", 42L)
        val orchestrator = SentinelDeviceUtilitiesOrchestrator(
            discoverCandidates = { listOf(candidate) },
            executeCandidate = {
                executions++
                SentinelCleanupPolicy.Result(it, it)
            },
            publishObserved = {}
        )

        val discovery = orchestrator.discover()

        assertEquals(1, discovery.plan.candidateCount)
        assertEquals(1, discovery.plan.directlyExecutableCount)
        assertEquals(42L, discovery.plan.knownReclaimableBytes)
        assertEquals(0, executions)
        assertTrue(discovery.observedSuccessfully)
    }

    @Test
    fun failedDiscoveryDoesNotPublishRuntimeObservation() {
        var observed = 0
        val orchestrator = SentinelDeviceUtilitiesOrchestrator(
            discoverCandidates = { error("collector failed") },
            executeCandidate = { error("must not execute") },
            publishObserved = { observed++ }
        )

        val discovery = orchestrator.discover()

        assertEquals(0, observed)
        assertEquals(0, discovery.plan.candidateCount)
        assertFalse(discovery.observedSuccessfully)
    }

    @Test
    fun cleanupExecutesOnlyDirectlyExecutableCandidatesAndSummarizesVerifiedResults() {
        val executable = candidate("/tmp/cache-a", 42L)
        val inaccessible = candidate("/tmp/external", 99L).copy(
            scope = SentinelCleanupPolicy.Scope.EXTERNAL_APP_PRIVATE_DATA,
            state = SentinelCleanupPolicy.ActionState.NOT_ACCESSIBLE
        )
        val executed = mutableListOf<String>()
        val orchestrator = SentinelDeviceUtilitiesOrchestrator(
            discoverCandidates = { listOf(executable, inaccessible) },
            executeCandidate = { before ->
                executed += before.stableId
                SentinelCleanupPolicy.Result(
                    before = before,
                    after = before.copy(bytes = 0L, state = SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED),
                    executionEffect = SentinelCleanupPolicy.ExecutionEffect.REMOVED_BY_EXECUTION
                )
            },
            publishObserved = {}
        )

        val run = orchestrator.cleanup(orchestrator.discover())

        assertEquals(listOf(executable.stableId), executed)
        assertEquals(1, run.summary.requestedCount)
        assertEquals(1, run.summary.verifiedRemovedCount)
        assertEquals(42L, run.summary.knownVerifiedFreedBytes)
        assertTrue(run.summary.isFullyVerified)
    }

    @Test
    fun observationPublicationDoesNotClaimCleanupSuccess() {
        var observed = 0
        val orchestrator = SentinelDeviceUtilitiesOrchestrator(
            discoverCandidates = { emptyList() },
            executeCandidate = { error("must not execute") },
            publishObserved = { observed++ }
        )

        val discovery = orchestrator.discover()
        val run = orchestrator.cleanup(discovery)

        assertEquals(2, observed)
        assertEquals(0, run.summary.requestedCount)
        assertFalse(run.summary.isFullyVerified)
    }

    private fun candidate(path: String, bytes: Long) = SentinelCleanupPolicy.Candidate(
        stableId = path,
        scope = SentinelCleanupPolicy.Scope.SENTINEL_CACHE,
        displayName = path.substringAfterLast('/'),
        bytes = bytes,
        state = SentinelCleanupPolicy.ActionState.EXECUTABLE,
        observedLastModifiedEpochMillis = 1_000L
    )
}
