package com.sentinel.quantum.security

import android.content.Context
import java.io.File

/** Bounded discovery of allowlisted disposable copies; no MMS cache traversal. */
class SentinelOwnedCleanupCollector internal constructor(private val roots: List<File>, private val clockMillis: () -> Long) {
    constructor(context: Context) : this(listOfNotNull(context.applicationContext.cacheDir, context.applicationContext.externalCacheDir), System::currentTimeMillis)

    data class Discovery(val candidates: List<SentinelCleanupPolicy.Candidate>, val coverageComplete: Boolean, val limitReached: Boolean)

    fun discover(): List<SentinelCleanupPolicy.Candidate> = scan().candidates

    fun scan(maxEntries: Int = 2000): Discovery {
        require(maxEntries in 1..5000)
        val found = linkedMapOf<String, SentinelCleanupPolicy.Candidate>()
        var complete = true
        var limit = false
        var visited = 0
        val now = clockMillis()
        fun entries(directory: File): List<File> {
            if (!directory.exists()) return emptyList()
            val listed = runCatching { directory.listFiles() }.getOrNull()
            if (listed == null) complete = false
            return listed?.toList().orEmpty()
        }
        fun visit(file: File) {
            if (visited >= maxEntries || Thread.currentThread().isInterrupted) { complete = false; limit = true; return }
            visited++
            when (SentinelCleanupEligibility.assess(file, roots)) {
                SentinelCleanupEligibility.Assessment.INSPECTION_FAILED -> { complete = false; return }
                SentinelCleanupEligibility.Assessment.PROTECTED -> return
                SentinelCleanupEligibility.Assessment.ALLOWED -> Unit
            }
            runCatching {
                if (!file.isFile) return
                val modified = file.lastModified()
                if (!SentinelCleanupEligibility.isOldEnough(modified, now)) return
                found[file.absolutePath] = SentinelCleanupPolicy.Candidate(
                    stableId = file.absolutePath, scope = SentinelCleanupPolicy.Scope.SENTINEL_CACHE,
                    displayName = file.name, bytes = file.length(), state = SentinelCleanupPolicy.ActionState.EXECUTABLE,
                    observedLastModifiedEpochMillis = modified
                )
            }.onFailure { complete = false }
        }
        for (rawRoot in roots.distinctBy { it.absolutePath }) {
            val root = try {
                if (!rawRoot.exists()) continue
                rawRoot.canonicalFile
            } catch (_: SecurityException) { complete = false; continue }
              catch (_: java.io.IOException) { complete = false; continue }
            for (file in entries(root)) {
                visit(file)
                if (limit) break
                if (file.name in SentinelCleanupEligibility.exportDirectories) {
                    val accessible = runCatching { file.absoluteFile == file.canonicalFile && file.isDirectory }
                    if (accessible.isFailure) complete = false
                    if (accessible.getOrDefault(false)) {
                        for (copy in entries(file)) { visit(copy); if (limit) break }
                    }
                }
                if (limit) break
            }
            if (limit) break
        }
        return Discovery(found.values.sortedBy { it.stableId }, complete, limit)
    }
}
