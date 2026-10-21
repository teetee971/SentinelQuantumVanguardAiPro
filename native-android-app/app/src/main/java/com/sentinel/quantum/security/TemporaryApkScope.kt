package com.sentinel.quantum.security

import java.io.File

/** Guarantees deletion attempts on normal completion, failures and coroutine cancellation.
 * The block must cooperate with cancellation; this does not interrupt blocked OS I/O.
 */
internal inline fun <T> withTemporaryApk(directory: File, block: (File) -> T): T {
    val file = File.createTempFile("sentinel_apk_inspection_", ".apk", directory)
    return try {
        block(file)
    } finally {
        if (file.exists() && !file.delete()) {
            // Fail closed: never represent unsuccessful temporary file cleanup as success.
            throw IllegalStateException("Échec de suppression du fichier temporaire APK")
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
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile }
            .filter { it.name.startsWith("sentinel_apk_inspection_") && it.name.endsWith(".apk") }
            .filter {
                val modified = it.lastModified()
                modified > 0L && modified <= nowMillis &&
                    nowMillis - modified >= minAgeMillis
            }
            .sortedBy { it.name }
            .toList()
    }
}
