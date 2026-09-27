package com.sentinel.quantum.security

import java.io.File

/**
 * Pure containment rule shared by cleanup execution and unit tests.
 */
object SentinelCleanupPathPolicy {
    fun isStrictChild(target: File, root: File): Boolean {
        val targetPath = runCatching { target.canonicalFile.toPath() }.getOrNull() ?: return false
        val rootPath = runCatching { root.canonicalFile.toPath() }.getOrNull() ?: return false
        return targetPath.startsWith(rootPath) && targetPath != rootPath
    }
}
