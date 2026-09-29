package com.sentinel.quantum.security

import android.content.Context
import java.io.File

/**
 * Discovers only Sentinel-owned cache/temp files. It does not traverse another
 * application's private sandbox.
 */
class SentinelOwnedCleanupCollector(context: Context) {
    private val appContext = context.applicationContext

    data class Discovery(
        val candidates: List<SentinelCleanupPolicy.Candidate>,
        val rootCount: Int,
        val unreadableRootCount: Int
    ) {
        init {
            require(rootCount >= 0)
            require(unreadableRootCount in 0..rootCount)
        }

        val isComplete: Boolean
            get() = rootCount > 0 && unreadableRootCount == 0
    }

    private data class RootDiscovery(
        val candidates: List<SentinelCleanupPolicy.Candidate>,
        val complete: Boolean
    )

    fun discover(): List<SentinelCleanupPolicy.Candidate> = discoverDetailed().candidates

    fun discoverDetailed(): Discovery {
        val roots = listOfNotNull(
            appContext.cacheDir,
            appContext.externalCacheDir
        ).distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }

        var unreadableRoots = 0
        val candidates = roots.flatMap { root ->
            val result = discoverFiles(root)
            if (!result.complete) unreadableRoots += 1
            result.candidates
        }

        return Discovery(
            candidates = candidates,
            rootCount = roots.size,
            unreadableRootCount = unreadableRoots
        )
    }

    private fun discoverFiles(root: File): RootDiscovery {
        val usableRoot = runCatching { root.exists() && root.isDirectory }.getOrDefault(false)
        if (!usableRoot) return RootDiscovery(emptyList(), complete = false)

        val rootCanonical =
            runCatching { root.canonicalFile }.getOrNull()
                ?: return RootDiscovery(emptyList(), complete = false)

        return runCatching {
            var complete = true
            val candidates = root.walkTopDown()
                .mapNotNull { file ->
                    val isFile = runCatching { file.isFile }.getOrElse {
                        complete = false
                        false
                    }
                    if (!isFile) return@mapNotNull null

                    val canonical = runCatching { file.canonicalFile }.getOrElse {
                        complete = false
                        return@mapNotNull null
                    }
                    if (!SentinelCleanupPathPolicy.isStrictChild(canonical, rootCanonical)) {
                        return@mapNotNull null
                    }

                    val bytes = runCatching { canonical.length() }.getOrElse {
                        complete = false
                        null
                    }
                    val modified = runCatching { canonical.lastModified() }.getOrElse {
                        complete = false
                        null
                    }
                    val evidenceComplete = bytes != null && modified != null
                    if (!evidenceComplete) complete = false

                    SentinelCleanupPolicy.Candidate(
                        stableId = canonical.absolutePath,
                        scope = SentinelCleanupPolicy.Scope.SENTINEL_CACHE,
                        displayName = file.name,
                        bytes = bytes,
                        state = if (evidenceComplete) {
                            SentinelCleanupPolicy.ActionState.EXECUTABLE
                        } else {
                            SentinelCleanupPolicy.ActionState.USER_CONFIRMATION_REQUIRED
                        },
                        observedLastModifiedEpochMillis = modified
                    )
                }
                .toList()

            RootDiscovery(candidates, complete)
        }.getOrElse {
            RootDiscovery(emptyList(), complete = false)
        }
    }
}
