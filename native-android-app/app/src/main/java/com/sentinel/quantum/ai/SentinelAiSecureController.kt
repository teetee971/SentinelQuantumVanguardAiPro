package com.sentinel.quantum.ai

import java.util.UUID

/**
 * In-memory authorization coordinator.
 *
 * A session identifier is a capability, not proof of user identity.
 * The caller must authenticate the human confirmation separately.
 */
class SentinelAiSecureController(
    private val clock: () -> Long
) {
    data class Approval(
        val token: String,
        val sessionId: String,
        val actionId: String,
        val parameters: String,
        val createdAtMs: Long,
        val expiresAtMs: Long
    )

    private val activeSessions = mutableSetOf<String>()
    private val pending = mutableMapOf<String, Approval>()
    private var lastObservedTimeMs = -1L
    private var clockInvalid = false

    companion object {
        const val MAX_PENDING = 128
        const val MAX_SESSIONS = 128
        const val MAX_TTL_MS = 60_000L
    }

    private fun trustedNow(): Long {
        check(!clockInvalid) { "Clock invalidated" }
        val now = clock()
        if (now < 0L || now < lastObservedTimeMs) {
            clockInvalid = true
            activeSessions.clear()
            pending.clear()
            throw IllegalStateException("Clock rollback")
        }
        lastObservedTimeMs = now
        return now
    }

    @Synchronized
    fun openSession(): String {
        trustedNow()
        check(activeSessions.size < MAX_SESSIONS) {
            "Too many active sessions"
        }

        val id = UUID.randomUUID().toString()
        check(activeSessions.add(id))
        return id
    }

    @Synchronized
    fun request(
        sessionId: String,
        actionId: String,
        parameters: String,
        ttlMs: Long = 30_000L
    ): Approval {
        require(actionId.isNotBlank())
        require(ttlMs in 1L..MAX_TTL_MS)

        val now = trustedNow()
        check(sessionId in activeSessions) {
            "Unknown or revoked session"
        }
        check(now <= Long.MAX_VALUE - ttlMs)

        pending.entries.removeAll {
            now >= it.value.expiresAtMs
        }
        check(pending.size < MAX_PENDING) {
            "Too many pending approvals"
        }

        val approval = Approval(
            UUID.randomUUID().toString(),
            sessionId,
            actionId,
            parameters,
            now,
            now + ttlMs
        )
        pending[approval.token] = approval
        return approval
    }

    @Synchronized
    fun confirm(
        token: String,
        sessionId: String,
        actionId: String,
        parameters: String
    ): Boolean {
        val now = trustedNow()
        val approval = pending.remove(token) ?: return false

        return sessionId in activeSessions &&
            now >= approval.createdAtMs &&
            now < approval.expiresAtMs &&
            approval.sessionId == sessionId &&
            approval.actionId == actionId &&
            approval.parameters == parameters
    }

    /**
     * Reject exactly one pending approval.
     *
     * Does not revoke unrelated sessions or approvals.
     */
    @Synchronized
    fun reject(
        token: String,
        sessionId: String
    ): Boolean {
        trustedNow()
        val approval = pending[token] ?: return false

        if (approval.sessionId != sessionId) {
            return false
        }

        pending.remove(token)
        return true
    }

    @Synchronized
    fun revokeSession(sessionId: String) {
        activeSessions.remove(sessionId)
        pending.entries.removeAll {
            it.value.sessionId == sessionId
        }
    }

    @Synchronized
    fun revokeAll() {
        activeSessions.clear()
        pending.clear()
    }

    @Synchronized
    fun pendingCount(): Int = pending.size

    @Synchronized
    fun activeSessionCount(): Int = activeSessions.size
}
