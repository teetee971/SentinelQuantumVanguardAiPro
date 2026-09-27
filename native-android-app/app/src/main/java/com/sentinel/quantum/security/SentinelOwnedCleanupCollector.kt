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
        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return emptyList()
        return runCatching {
            root.walkTopDown()
                .filter { it.isFile }
                .mapNotNull { file ->
                    val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return@mapNotNull null
                    val rootPath = rootCanonical.toPath()
                    val filePath = canonical.toPath()
                    if (!filePath.startsWith(rootPath) || filePath == rootPath) return@mapNotNull null

                    SentinelCleanupPolicy.Candidate(
                        stableId = canonical.absolutePath,
                        scope = SentinelCleanupPolicy.Scope.SENTINEL_CACHE,
                        displayName = file.name,
                        bytes = runCatching { canonical.length() }.getOrNull(),
                        state = SentinelCleanupPolicy.ActionState.EXECUTABLE,
                        observedLastModifiedEpochMillis = runCatching { canonical.lastModified() }.getOrNull()
                    )
                }
                .toList()
        }.getOrDefault(emptyList())
    }
}
