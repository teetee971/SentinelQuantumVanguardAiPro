package com.sentinel.quantum.security

import android.content.Context
import java.io.File

/**
 * Executes cleanup only inside Sentinel-owned cache roots and verifies the
 * post-condition before reporting success.
 */
class SentinelOwnedCleanupExecutor internal constructor(private val roots: List<File>, private val clockMillis: () -> Long) {
    constructor(context: Context) : this(listOfNotNull(context.applicationContext.cacheDir, context.applicationContext.externalCacheDir), System::currentTimeMillis)

    fun execute(candidate: SentinelCleanupPolicy.Candidate): SentinelCleanupPolicy.Result {
        if (!SentinelCleanupPolicy.canExecuteDirectly(candidate)) {
            return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(state = SentinelCleanupPolicy.ActionState.NOT_ACCESSIBLE)
            )
        }

        val target = File(candidate.stableId)
        if (!isInsideAllowedCacheRoot(target) || !SentinelCleanupEligibility.isAllowedPath(target, roots)) {
            return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(state = SentinelCleanupPolicy.ActionState.NOT_ACCESSIBLE)
            )
        }

        if (!target.exists()) {
            return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(bytes = 0L, state = SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED),
                SentinelCleanupPolicy.ExecutionEffect.ALREADY_ABSENT
            )
        }

        if (!runCatching { target.isFile }.getOrDefault(false)) {
            return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(
                    bytes = runCatching { target.length() }.getOrNull(),
                    state = SentinelCleanupPolicy.ActionState.USER_CONFIRMATION_REQUIRED
                )
            )
        }

        val currentBytes = runCatching { target.length() }.getOrNull()
        val currentModified = runCatching { target.lastModified() }.getOrNull()
        val isFresh = currentModified != null && SentinelCleanupEligibility.isOldEnough(currentModified, clockMillis()) && SentinelCleanupFreshnessPolicy.isFresh(
            discovered = SentinelCleanupFreshnessPolicy.Observation(
                bytes = candidate.bytes,
                lastModifiedEpochMillis = candidate.observedLastModifiedEpochMillis
            ),
            current = SentinelCleanupFreshnessPolicy.Observation(
                bytes = currentBytes,
                lastModifiedEpochMillis = currentModified
            )
        )
        if (!isFresh) {
            return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(
                    bytes = currentBytes,
                    state = SentinelCleanupPolicy.ActionState.USER_CONFIRMATION_REQUIRED,
                    observedLastModifiedEpochMillis = currentModified
                )
            )
        }

        val attempted = runCatching { target.delete() }.getOrDefault(false)
        val stillExists = runCatching { target.exists() }.getOrDefault(true)
        val after = when {
            !stillExists -> candidate.copy(bytes = 0L, state = SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED)
            attempted -> candidate.copy(
                bytes = runCatching { target.length() }.getOrNull(),
                state = SentinelCleanupPolicy.ActionState.EXECUTED_UNVERIFIED
            )
            else -> candidate.copy(
                bytes = runCatching { target.length() }.getOrNull(),
                state = SentinelCleanupPolicy.ActionState.FAILED
            )
        }
        return SentinelCleanupPolicy.Result(
            candidate,
            after,
            if (!stillExists && attempted) {
                SentinelCleanupPolicy.ExecutionEffect.REMOVED_BY_EXECUTION
            } else {
                SentinelCleanupPolicy.ExecutionEffect.NONE
            }
        )
    }

    internal fun isInsideAllowedCacheRoot(target: File): Boolean =
        allowedRoots().any { root -> SentinelCleanupPathPolicy.isStrictChild(target, root) }

    private fun allowedRoots(): List<File> = roots.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
}
