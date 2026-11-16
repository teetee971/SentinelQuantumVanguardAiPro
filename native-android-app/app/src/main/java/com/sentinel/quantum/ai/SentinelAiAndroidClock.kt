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

    @Deprecated("Use createOwner with trusted authentication and action catalog")
    fun createController(): SentinelAiSecureController =
        SentinelAiSecureController(
            clock = ::elapsedRealtimeMs
        )
    fun createOwner(
        actions: Map<String, SentinelAiActionPolicy>,
        authenticated: (SentinelAiApprovalAuthority.Proposal) -> Boolean
    ): SentinelAiApprovalPorts.Owner = SentinelAiApprovalPorts.createOwner(
        ::elapsedRealtimeMs, actions, authenticated
    )
}
