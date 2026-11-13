package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

class TemporaryApkScopeTest {
    private fun check(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("sentinel-cleanup-test").toFile()
        try {
            val file = File(directory, "sentinel_apk_inspection_missing.apk")
            block(directory)
            assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith("sentinel_apk_inspection_") })
        } finally { directory.deleteRecursively() }
    }

    @Test fun cleansAfterSuccess() = check { dir ->
        withTemporaryApk(dir) { it.writeText("test") }
    }

    @Test fun cleansAfterCancellation() = check { dir ->
        assertThrows(CancellationException::class.java) {
            withTemporaryApk(dir) { it.writeText("partial"); throw CancellationException("cancelled") }
        }
    }

    @Test fun cleansAfterFailure() = check { dir ->
        assertThrows(IllegalArgumentException::class.java) {
            withTemporaryApk(dir) { it.writeText("partial"); throw IllegalArgumentException("invalid") }
        }
    }
}
