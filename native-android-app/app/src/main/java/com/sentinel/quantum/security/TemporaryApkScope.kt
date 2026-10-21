package com.sentinel.quantum.security

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Tracks active inspections within the current app process. Does not assert cross-process locking. */
internal object ActiveTemporaryApks {
    private val paths = ConcurrentHashMap.newKeySet<String>()
    fun enter(file: File) { check(paths.add(file.absolutePath)) { "APK déjà actif" } }
    fun leave(file: File) { paths.remove(file.absolutePath) }
    fun contains(file: File): Boolean = paths.contains(file.absolutePath)
}

/** Guarantees deletion attempts on normal completion, failures and coroutine cancellation.
 * The block must cooperate with cancellation; this does not interrupt blocked OS I/O.
 */
internal fun <T> withTemporaryApk(directory: File, block: (File) -> T): T {
    return ApkInspectionProcessGuard.exclusive(directory) {
    val file = File.createTempFile("sentinel_apk_inspection_", ".apk", directory)
    ActiveTemporaryApks.enter(file)
    return try {
        block(file)
    } finally {
        ActiveTemporaryApks.leave(file)
        if (file.exists() && !file.delete()) {
            // Fail closed: never represent unsuccessful temporary file cleanup as success.
            throw IllegalStateException("Échec de suppression du fichier temporaire APK")
        }
    }
    }
}

/**
 * Read-only inventory of possibly abandoned inspection files.
 * No deletion is performed. A file is only a candidate when its age exceeds
 * the threshold; the caller must still rule out an active inspector.
 */
internal object TemporaryApkInventory {
    fun candidates(directory: File, nowMillis: Long, minAgeMillis: Long): List<File> {
        require(minAgeMillis > 0L)
        if (!directory.isDirectory || !ApkInspectionProcessGuard.available(directory)) return emptyList()
        return directory.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile }
            .filter { it.name.startsWith("sentinel_apk_inspection_") && it.name.endsWith(".apk") }
            .filterNot { ActiveTemporaryApks.contains(it) }
            .filter {
                val modified = it.lastModified()
                modified > 0L && modified <= nowMillis &&
                    nowMillis - modified >= minAgeMillis
            }
            .sortedBy { it.name }
            .toList()
    }
}
