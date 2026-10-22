package com.sentinel.quantum.security

import java.io.File
import java.io.IOException

/**
 * The copied APK must not survive a completed inspection.
 * Deletion failures must be visible instead of relying on deleteOnExit on Android.
 */
inline fun <T> withTemporaryApk(
    cacheDir: File,
    block: (File) -> T
): T {
    val file = File.createTempFile("sentinel_apk_", ".apk", cacheDir)
    var failure: Throwable? = null
    try {
        return block(file)
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        if (file.exists() && !file.delete()) {
            val cleanupError = IOException("Impossible de supprimer le fichier APK temporaire")
            if (failure != null) {
                failure.addSuppressed(cleanupError)
            } else {
                throw cleanupError
            }
        }
    }
}
