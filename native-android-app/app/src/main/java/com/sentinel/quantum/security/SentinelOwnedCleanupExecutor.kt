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

        if (!target.exists()) {
            return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(bytes = 0L, state = SentinelCleanupPolicy.ActionState.VERIFIED_REMOVED)
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

        val observedModified = candidate.observedLastModifiedEpochMillis
        val currentModified = runCatching { target.lastModified() }.getOrNull()
        if (observedModified != null && currentModified != observedModified) {
            return SentinelCleanupPolicy.Result(
                candidate,
                candidate.copy(
                    bytes = runCatching { target.length() }.getOrNull(),
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
        return SentinelCleanupPolicy.Result(candidate, after)
    }

    internal fun isInsideAllowedCacheRoot(target: File): Boolean =
        allowedRoots().any { root -> SentinelCleanupPathPolicy.isStrictChild(target, root) }

    private fun allowedRoots(): List<File> = listOfNotNull(
        appContext.cacheDir,
        appContext.externalCacheDir
    ).distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
}
