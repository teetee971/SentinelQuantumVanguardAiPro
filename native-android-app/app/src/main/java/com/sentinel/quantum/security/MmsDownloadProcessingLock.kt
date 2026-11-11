package com.sentinel.quantum.security

import java.util.concurrent.locks.ReentrantLock

/**
 * Bounded in-process lock shared by the download callback, the recovery worker and the cleanup worker.
 *
 * 64 stripes cap the memory. Two different file names can share a stripe and then wait for each other.
 * The same thread can re-enter a stripe, so cleanup may call recovery while holding the file.
 *
 * This does not cross process boundaries. The manifest declares no android:process, so these
 * components share the application process today. SharedPreferences MODE_PRIVATE is also
 * process-local. A future remote process would not be covered by this lock.
 */
internal object MmsDownloadProcessingLock {
    const val STRIPES = 64
    private val locks = Array(STRIPES) { ReentrantLock() }

    fun stripe(fileName: String): Int = (fileName.hashCode() and Int.MAX_VALUE) % STRIPES

    inline fun <T> withFile(fileName: String, block: () -> T): T {
        val lock = locks[stripe(fileName)]
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
