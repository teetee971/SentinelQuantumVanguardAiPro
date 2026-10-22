package com.sentinel.quantum.security

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException

/**
 * Cross-process advisory guard for the dedicated APK inspection cache directory.
 * Serializes inspections cooperating with this protocol. It cannot constrain other apps/processes
 * that ignore locks, or guarantee an interrupted system I/O will terminate.
 */
internal object ApkInspectionProcessGuard {
    fun <T> exclusive(directory: File, block: () -> T): T {
        require(directory.isDirectory) { "Cache APK indisponible" }
        val lockFile = File(directory, ".sentinel_apk_inspection.lock")
        RandomAccessFile(lockFile, "rw").use { handle ->
            val lock = try {
                handle.channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            check(lock != null) { "Une autre inspection APK utilise déjà le cache" }
            try {
                return block()
            } finally {
                lock.release()
            }
        }
    }

    fun available(directory: File): Boolean {
        if (!directory.isDirectory) return false
        return try {
            exclusive(directory) { true }
        } catch (_: IllegalStateException) {
            false
        } catch (_: java.io.IOException) {
            false
        }
    }
}
