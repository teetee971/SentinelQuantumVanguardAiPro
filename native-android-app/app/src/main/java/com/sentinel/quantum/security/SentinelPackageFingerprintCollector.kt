package com.sentinel.quantum.security

import android.content.pm.PackageInfo
import java.io.File
import java.io.FileInputStream

/**
 * Fingerprints only the package artifact Android exposes through ApplicationInfo.
 * Read failures are explicit and never interpreted as a clean artifact.
 */
object SentinelPackageFingerprintCollector {

    sealed interface Outcome {
        data class Hashed(val sha256: String, val sizeBytes: Long) : Outcome
        data class NotAccessible(val reason: String) : Outcome
    }

    fun fingerprintBaseApk(packageInfo: PackageInfo): Outcome {
        val sourceDir = packageInfo.applicationInfo?.sourceDir
            ?: return Outcome.NotAccessible("source_dir_missing")
        val file = File(sourceDir)
        if (!file.isFile || !file.canRead()) {
            return Outcome.NotAccessible("base_apk_not_readable")
        }

        return runCatching {
            FileInputStream(file).use { input ->
                Outcome.Hashed(
                    sha256 = SentinelSha256.digest(input),
                    sizeBytes = file.length().coerceAtLeast(0L)
                )
            }
        }.getOrElse { error ->
            Outcome.NotAccessible(error.javaClass.simpleName.ifBlank { "read_failed" })
        }
    }
}
