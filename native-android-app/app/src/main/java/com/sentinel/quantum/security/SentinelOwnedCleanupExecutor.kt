package com.sentinel.quantum.security

import android.content.Context
import java.io.File

/**
 * Executes cleanup only inside Sentinel-owned cache roots and verifies the
 * post-condition before reporting success.
 */
class SentinelOwnedCleanupExecutor(context: Context) {
    private val appContext = context.applicationContext

    fun execute(candidate: SentinelCleanupPolicy.Candidate): SentinelCleanupPolicy.Result {
        if (!SentinelCleanupPolicy.canExecuteDirectly(candidate)) {
            return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(state = SentinelCleanupPolicy.ActionState.NOT_ACCESSIBLE)
            )
        }

        val target = File(candidate.stableId)
        if (!isInsideAllowedCacheRoot(target)) {
            return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(state = SentinelCleanupPolicy.ActionState.NOT_ACCESSIBLE)
            )
        }

        val existsBefore = runCatching { target.exists() }.getOrNull()
            ?: return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(state = SentinelCleanupPolicy.ActionState.NOT_ACCESSIBLE)
            )

        if (!existsBefore) {
            return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(bytes = 0L, state = SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED),
                SentinelCleanupPolicy.ExecutionEffect.ALREADY_ABSENT
            )
        }

        val isFile = runCatching { target.isFile }.getOrNull()
            ?: return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(state = SentinelCleanupPolicy.ActionState.NOT_ACCESSIBLE)
            )

        if (!isFile) {
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
        val isFresh = SentinelCleanupFreshnessPolicy.isFresh(
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
        val existsAfter = runCatching { target.exists() }.getOrNull()
        val after = when {
            existsAfter == false ->
                candidate.copy(bytes = 0L, state = SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED)
            existsAfter == null && attempted ->
                candidate.copy(bytes = null, state = SentinelCleanupPolicy.ActionState.EXECUTED_UNVERIFIED)
            existsAfter == null ->
                candidate.copy(bytes = null, state = SentinelCleanupPolicy.ActionState.FAILED)
            attempted ->
                candidate.copy(
                    bytes = runCatching { target.length() }.getOrNull(),
                    state = SentinelCleanupPolicy.ActionState.EXECUTED_UNVERIFIED
                )
            else ->
                candidate.copy(
                    bytes = runCatching { target.length() }.getOrNull(),
                    state = SentinelCleanupPolicy.ActionState.FAILED
                )
        }

        return SentinelCleanupPolicy.Result(
            candidate,
            after,
            if (existsAfter == false && attempted) {
                SentinelCleanupPolicy.ExecutionEffect.REMOVED_BY_EXECUTION
            } else {
                SentinelCleanupPolicy.ExecutionEffect.NONE
            }
        )
    }

    internal fun isInsideAllowedCacheRoot(target: File): Boolean =
        allowedRoots().any { root -> SentinelCleanupPathPolicy.isStrictChild(target, root) }

    private fun allowedRoots(): List<File> = listOfNotNull(
        appContext.cacheDir,
        appContext.externalCacheDir
    ).distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
}
