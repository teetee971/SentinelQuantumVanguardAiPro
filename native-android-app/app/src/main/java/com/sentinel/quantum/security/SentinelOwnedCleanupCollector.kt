package com.sentinel.quantum.security

import android.content.Context
import java.io.File

/**
 * Discovers only Sentinel-owned cache/temp files. It does not traverse another
 * application's private sandbox.
 */
class SentinelOwnedCleanupCollector(context: Context) {
    private val appContext = context.applicationContext

    fun discover(): List<SentinelCleanupPolicy.Candidate> {
        val roots = listOfNotNull(
            appContext.cacheDir,
            appContext.externalCacheDir
        ).distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }

        return roots.flatMap { root -> discoverFiles(root) }
    }

    private fun discoverFiles(root: File): List<SentinelCleanupPolicy.Candidate> {
        if (!root.exists() || !root.isDirectory) return emptyList()
        return runCatching {
            root.walkTopDown()
                .filter { it.isFile }
                .map { file ->
                    SentinelCleanupPolicy.Candidate(
                        stableId = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath),
                        scope = SentinelCleanupPolicy.Scope.SENTINEL_CACHE,
                        displayName = file.name,
                        bytes = runCatching { file.length() }.getOrNull(),
                        state = SentinelCleanupPolicy.ActionState.EXECUTABLE
                    )
                }
                .toList()
        }.getOrDefault(emptyList())
    }
}
