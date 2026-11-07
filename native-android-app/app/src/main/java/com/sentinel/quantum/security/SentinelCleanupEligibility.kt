package com.sentinel.quantum.security

import java.io.File

/** Only disposable Sentinel copies. In particular, transport/recovery cache is never eligible. */
internal object SentinelCleanupEligibility {
    const val MIN_AGE_MILLIS = 86_400_000L
    val exportDirectories = setOf("sentinel_log_export", "sentinel_sms_export")

    enum class Assessment { ALLOWED, PROTECTED, INSPECTION_FAILED }

    fun isAllowedPath(target: File, roots: List<File>): Boolean = assess(target, roots) == Assessment.ALLOWED

    fun assess(target: File, roots: List<File>): Assessment = try {
        val absolute = target.absoluteFile
        // Do not follow either file or directory symlinks, or normalize a supplied traversal.
        if (absolute != target.canonicalFile) return Assessment.PROTECTED
        val allowed = roots.any { rawRoot ->
            val root = rawRoot.canonicalFile
            (absolute.parentFile == root && absolute.name.startsWith("sentinel_apk_inspection_") && absolute.name.endsWith(".apk")) ||
                (absolute.parentFile?.parentFile == root && absolute.parentFile?.name in exportDirectories)
        }
        if (allowed) Assessment.ALLOWED else Assessment.PROTECTED
    } catch (_: java.io.IOException) { Assessment.INSPECTION_FAILED }
      catch (_: SecurityException) { Assessment.INSPECTION_FAILED }

    fun isOldEnough(modified: Long, now: Long): Boolean =
        modified > 0 && now >= modified && now - modified >= MIN_AGE_MILLIS
}
