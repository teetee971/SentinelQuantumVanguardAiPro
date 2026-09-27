package com.sentinel.quantum.security

import android.os.Environment
import android.os.StatFs

/** Permission-free measurement of the primary data filesystem. */
data class SentinelStorageSnapshot(
    val totalBytes: Long?,
    val availableBytes: Long?,
    val observedAtEpochMillis: Long
) {
    companion object {
        fun capture(nowEpochMillis: Long = System.currentTimeMillis()): SentinelStorageSnapshot {
            return runCatching {
                val stat = StatFs(Environment.getDataDirectory().absolutePath)
                SentinelStorageSnapshot(
                    totalBytes = stat.totalBytes,
                    availableBytes = stat.availableBytes,
                    observedAtEpochMillis = nowEpochMillis
                )
            }.getOrElse {
                SentinelStorageSnapshot(null, null, nowEpochMillis)
            }
        }
    }
}
