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
