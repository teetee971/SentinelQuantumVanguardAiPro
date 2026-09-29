package com.sentinel.quantum.security

/**
 * Aggregates independently verified cleanup results. Partial success stays
 * explicit; failures and unresolved actions are never folded into "cleaned".
 */
object SentinelCleanupBatch {
    data class Summary(
        val requestedCount: Int,
        val verifiedRemovedCount: Int,
        val unresolvedCount: Int,
        val failedCount: Int,
        val knownVerifiedFreedBytes: Long,
        val unknownFreedSizeCount: Int
    ) {
        val isFullyVerified: Boolean
            get() = requestedCount > 0 &&
                verifiedRemovedCount == requestedCount &&
                unresolvedCount == 0 &&
                failedCount == 0
    }

    fun summarize(results: List<SentinelCleanupPolicy.Result>): Summary {
        val verified = results.filter { it.isVerifiedCleaned }
        return Summary(
            requestedCount = results.size,
            verifiedRemovedCount = verified.size,
            unresolvedCount = results.count {
                it.after.state != SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED &&
                    it.after.state != SentinelCleanupPolicy.ActionState.FAILED
            },
            failedCount = results.count {
                it.after.state == SentinelCleanupPolicy.ActionState.FAILED
            },
            knownVerifiedFreedBytes = verified.mapNotNull { it.verifiedFreedBytes }.sum(),
            unknownFreedSizeCount = verified.count { it.verifiedFreedBytes == null }
        )
    }
}
