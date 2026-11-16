package com.sentinel.quantum.ai

import android.os.SystemClock

/**
 * Android monotonic clock for short-lived AI approvals.
 *
 * Approvals are process-local and must never be persisted.
 * A reboot or process restart invalidates all sessions.
 */
object SentinelAiAndroidClock {

    fun elapsedRealtimeMs(): Long =
        SystemClock.elapsedRealtime()

    fun createController(): SentinelAiSecureController =
        SentinelAiSecureController(
            clock = ::elapsedRealtimeMs
        )
}
