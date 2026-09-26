package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.security.VerifiedWearableHandshake

/**
 * In-memory authority for wearable handshake/session lifecycle.
 *
 * One stable Sentinel identity may have at most one pending handshake and one authoritative
 * active session. Starting a new handshake invalidates the previous pending attempt; activating
 * it replaces the previous active session atomically.
 */
data class WearableHandshakeAttempt(
    val stableId: String,
    val attemptId: String,
    val startedAtMs: Long
) {
    init {
        require(stableId.isNotBlank())
        require(attemptId.isNotBlank())
        require(startedAtMs >= 0)
    }
}

enum class WearableSessionActivationDecision {
    ACTIVATED,
    NO_PENDING_HANDSHAKE,
    SUPERSEDED_HANDSHAKE,
    IDENTITY_MISMATCH
}

class WearableSessionAuthority {
    private val pendingByIdentity = mutableMapOf<String, WearableHandshakeAttempt>()
    private val activeByIdentity = mutableMapOf<String, WearableSession>()

    @Synchronized
    fun beginHandshake(attempt: WearableHandshakeAttempt) {
        pendingByIdentity[attempt.stableId] = attempt
    }

    @Synchronized
    fun activate(
        attempt: WearableHandshakeAttempt,
        session: WearableSession,
        verifiedProof: VerifiedWearableHandshake
    ): WearableSessionActivationDecision {
        val pending = pendingByIdentity[attempt.stableId]
            ?: return WearableSessionActivationDecision.NO_PENDING_HANDSHAKE
        if (pending.attemptId != attempt.attemptId) {
            return WearableSessionActivationDecision.SUPERSEDED_HANDSHAKE
        }
        if (verifiedProof.stableId != attempt.stableId) {
            return WearableSessionActivationDecision.IDENTITY_MISMATCH
        }
        if (verifiedProof.sessionId != session.sessionId) {
            return WearableSessionActivationDecision.IDENTITY_MISMATCH
        }
        activeByIdentity[attempt.stableId] = session
        pendingByIdentity.remove(attempt.stableId)
        return WearableSessionActivationDecision.ACTIVATED
    }

    @Synchronized
    fun activeSession(stableId: String): WearableSession? = activeByIdentity[stableId]

    @Synchronized
    fun revoke(stableId: String) {
        pendingByIdentity.remove(stableId)
        activeByIdentity.remove(stableId)
    }
}
