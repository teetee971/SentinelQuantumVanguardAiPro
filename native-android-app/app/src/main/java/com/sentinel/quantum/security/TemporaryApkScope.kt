package com.sentinel.quantum.security

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal object ActiveTemporaryApks {
    private val paths = ConcurrentHashMap.newKeySet<String>()

    fun enter(file: File) {
        check(paths.add(file.absolutePath)) { "APK déjà actif" }
    }

    fun leave(file: File) {
        paths.remove(file.absolutePath)
    }

    fun contains(file: File): Boolean =
        paths.contains(file.absolutePath)
}

internal fun <T> withTemporaryApk(
    directory: File,
    block: (File) -> T
): T = ApkInspectionProcessGuard.exclusive(directory) {
    val file = File.createTempFile(
        "sentinel_apk_inspection_", ".apk", directory
    )

    ActiveTemporaryApks.enter(file)

    var failure: Throwable? = null

    try {
        block(file)
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        ActiveTemporaryApks.leave(file)

        if (file.exists() && !file.delete()) {
            val cleanupError = IOException(
                "Impossible de supprimer le fichier APK temporaire"
            )

            if (failure != null) {
                failure.addSuppressed(cleanupError)
            } else {
                throw cleanupError
            }
        }
    }
}

internal object TemporaryApkInventory {
    fun candidates(
        directory: File,
        nowMillis: Long,
        minAgeMillis: Long
    ): List<File> {
        require(minAgeMillis > 0L)

        if (!directory.isDirectory ||
            !ApkInspectionProcessGuard.available(directory)
        ) return emptyList()

        return directory.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile }
            .filter {
                it.name.startsWith("sentinel_apk_inspection_") &&
                it.name.endsWith(".apk")
            }
            .filterNot { ActiveTemporaryApks.contains(it) }
            .filter {
                val modified = it.lastModified()
                modified > 0L &&
                modified <= nowMillis &&
                nowMillis - modified >= minAgeMillis
            }
            .sortedBy { it.name }
            .toList()
    }
}
