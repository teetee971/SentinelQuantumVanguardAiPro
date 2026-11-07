package com.sentinel.quantum.security

import java.io.File
import java.io.IOException

/** Streams entries and closes the directory handle even when the visitor stops early. */
internal fun interface SentinelCleanupDirectoryEntries {
    fun forEach(directory: File, visitor: (File) -> Boolean)

    companion object {
        fun streaming(): SentinelCleanupDirectoryEntries = Api26
        fun forAndroid(sdk: Int): SentinelCleanupDirectoryEntries =
            if (sdk >= 26) Api26 else Unavailable
    }

    private object Unavailable : SentinelCleanupDirectoryEntries {
        override fun forEach(directory: File, visitor: (File) -> Boolean) {
            throw IOException("Bounded directory streaming requires Android 8 or later")
        }
    }

    @android.annotation.TargetApi(26)
    private object Api26 : SentinelCleanupDirectoryEntries {
        override fun forEach(directory: File, visitor: (File) -> Boolean) {
            java.nio.file.Files.newDirectoryStream(directory.toPath()).use { stream ->
                for (entry in stream) if (!visitor(entry.toFile())) break
            }
        }
    }
}
