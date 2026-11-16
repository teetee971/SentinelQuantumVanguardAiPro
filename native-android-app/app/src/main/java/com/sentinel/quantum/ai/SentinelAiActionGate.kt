package com.sentinel.quantum.ai

import java.util.UUID

/**
 * One-use confirmation gate.
 *
 * The caller must supply a monotonic timestamp from the same
 * clock source for every operation.
 *
 * No action is executed by this class.
 */
class SentinelAiActionGate {

    data class Request(
        val token: String,
        val actionId: String,
        val parameters: String,
        val expiresAtMs: Long,
        val createdAtMs: Long = 0L
    )

    private val pending = mutableMapOf<String, Request>()

    companion object {
        const val MAX_PENDING_REQUESTS = 128
    }

    @Synchronized
    fun request(
        actionId: String,
        parameters: String,
        nowMs: Long,
        ttlMs: Long = 30_000L
    ): Request {
        require(actionId.isNotBlank())
        require(nowMs >= 0L)
        require(ttlMs in 1L..60_000L)
        require(nowMs <= Long.MAX_VALUE - ttlMs)

        // Remove expired entries before enforcing capacity.
        pending.entries.removeAll {
            nowMs >= it.value.expiresAtMs
        }

        check(pending.size < MAX_PENDING_REQUESTS) {
            "Too many pending confirmations"
        }

        val request = Request(
            token = UUID.randomUUID().toString(),
            actionId = actionId,
            parameters = parameters,
            expiresAtMs = nowMs + ttlMs,
            createdAtMs = nowMs
        )

        pending[request.token] = request
        return request
    }

    /**
     * Consumes the request regardless of confirmation outcome.
     * Only a matching, unexpired request returns true.
     */
    @Synchronized
    fun confirm(
        token: String,
        actionId: String,
        parameters: String,
        nowMs: Long
    ): Boolean {
        val request = pending.remove(token) ?: return false

        return nowMs >= request.createdAtMs &&
            nowMs < request.expiresAtMs &&
            request.actionId == actionId &&
            request.parameters == parameters
    }

    @Synchronized
    fun revokeAll() {
        pending.clear()
    }

    @Synchronized
    fun pendingCount(): Int = pending.size
}
