package com.sentinel.quantum.security

/**
 * Aggregates cleanup candidates without inventing bytes for unknown sizes.
 */
object SentinelCleanupPlan {
    data class Summary(
        val candidateCount: Int,
        val directlyExecutableCount: Int,
        val inaccessibleCount: Int,
        val knownReclaimableBytes: Long,
        val unknownSizeCount: Int
    ) {
        val hasCompleteSizeEstimate: Boolean get() = unknownSizeCount == 0
    }

    fun summarize(candidates: List<SentinelCleanupPolicy.Candidate>): Summary {
        val reclaimable = candidates.filter {
            it.state != SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED
        }
        return Summary(
            candidateCount = reclaimable.size,
            directlyExecutableCount = reclaimable.count(SentinelCleanupPolicy::canExecuteDirectly),
            inaccessibleCount = reclaimable.count {
                it.state == SentinelCleanupPolicy.ActionState.NOT_ACCESSIBLE ||
                    SentinelCleanupPolicy.isUnsupportedDirectCleanup(it)
            },
            knownReclaimableBytes = reclaimable.mapNotNull { it.bytes }.sum(),
            unknownSizeCount = reclaimable.count { it.bytes == null }
        )
    }
}
