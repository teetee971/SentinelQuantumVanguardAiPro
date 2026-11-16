package com.sentinel.quantum.security

import android.content.Context
import com.sentinel.quantum.integration.IntegrationCapabilityId
import com.sentinel.quantum.integration.IntegrationDiagnosticReason
import com.sentinel.quantum.integration.IntegrationState
import com.sentinel.quantum.integration.IntegrationStatus
import com.sentinel.quantum.integration.IntegrationTruthRuntime

/**
 * End-to-end orchestration seam for Sentinel-owned storage cleanup.
 *
 * Discovery never deletes. Cleanup executes only candidates the cleanup policy explicitly marks
 * as directly executable, then aggregates post-condition-verified results. Observing this local
 * chain proves that the device-utilities runtime is wired, but it does not certify every broader
 * device utility on physical hardware; the product truth therefore remains PHYSICAL_REQUIRED.
 */
class SentinelDeviceUtilitiesOrchestrator(
    private val discoverCandidates: () -> List<SentinelCleanupPolicy.Candidate>,
    private val executeCandidate: (SentinelCleanupPolicy.Candidate) -> SentinelCleanupPolicy.Result,
    private val publishObserved: () -> Unit
) {
    constructor(context: Context) : this(
        discoverCandidates = { SentinelOwnedCleanupCollector(context.applicationContext).discover() },
        executeCandidate = { SentinelOwnedCleanupExecutor(context.applicationContext).execute(it) },
        publishObserved = {
            IntegrationTruthRuntime.publish(
                IntegrationCapabilityId.DEVICE_UTILITIES,
                IntegrationStatus(
                    IntegrationState.PHYSICAL_REQUIRED,
                    IntegrationDiagnosticReason.PHYSICAL_VALIDATION_REQUIRED
                )
            )
        }
    )

    data class Discovery(
        val candidates: List<SentinelCleanupPolicy.Candidate>,
        val plan: SentinelCleanupPlan.Summary,
        val observedSuccessfully: Boolean
    )

    data class CleanupRun(
        val results: List<SentinelCleanupPolicy.Result>,
        val summary: SentinelCleanupBatch.Summary
    )

    fun discover(): Discovery {
        val result = runCatching { discoverCandidates() }
        val candidates = result.getOrDefault(emptyList())
        if (result.isSuccess) publishObserved()
        return Discovery(
            candidates = candidates,
            plan = SentinelCleanupPlan.summarize(candidates),
            observedSuccessfully = result.isSuccess
        )
    }

    fun cleanup(discovery: Discovery): CleanupRun {
        if (!discovery.observedSuccessfully) {
            return CleanupRun(
                results = emptyList(),
                summary = SentinelCleanupBatch.summarize(emptyList())
            )
        }
        val executable = discovery.candidates.filter(SentinelCleanupPolicy::canExecuteDirectly)
        val results = executable.map { candidate ->
            runCatching { executeCandidate(candidate) }
                .getOrElse {
                    SentinelCleanupPolicy.Result(
                        before = candidate,
                        after = candidate.copy(state = SentinelCleanupPolicy.ActionState.FAILED)
                    )
                }
        }
        publishObserved()
        return CleanupRun(
            results = results,
            summary = SentinelCleanupBatch.summarize(results)
        )
    }
}
