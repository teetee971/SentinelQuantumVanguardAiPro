package com.sentinel.quantum.security

import java.io.File

/**
 * Canonical containment rule compatible with the app's Android minSdk.
 * A target must be a strict descendant, never the root itself or a sibling
 * sharing the same textual prefix.
 */
object SentinelCleanupPathPolicy {
    fun isStrictChild(target: File, root: File): Boolean {
        val targetPath = runCatching { target.canonicalPath }.getOrNull() ?: return false
        val rawRootPath = runCatching { root.canonicalPath }.getOrNull() ?: return false
        val rootPath = rawRootPath.trimEnd(File.separatorChar)
        if (rootPath.isEmpty()) return false
        return targetPath.startsWith(rootPath + File.separator)
    }
}
