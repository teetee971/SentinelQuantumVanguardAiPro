package com.sentinel.quantum.security

import java.io.File

inline fun <T> withTemporaryApk(
    cacheDir: File,
    block: (File) -> T
): T {
    val file = File.createTempFile("sentinel_apk_", ".apk", cacheDir)
    try {
        return block(file)
    } finally {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit()
        }
    }
}
