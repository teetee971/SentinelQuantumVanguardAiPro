package com.sentinel.quantum.security

/** A quiet Bluetooth scan stops early. It never runs longer than the maximum. */
object BluetoothScanPace {
    const val QUIET_MS = 2_500L
    const val MAX_MS = 10_000L

    fun remainingQuietMs(elapsedMs: Long, maxMs: Long = MAX_MS): Long {
        val left = (maxMs - elapsedMs).coerceAtLeast(0L)
        return QUIET_MS.coerceAtMost(left)
    }
}
