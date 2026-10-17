package com.sentinel.quantum.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local correlation for the non-privileged Wear OS platform probe.
 *
 * Raw Data Layer node ids are retained only in memory and only for the short lifetime of an
 * outstanding challenge. A matching pong proves app-to-app Data Layer reachability; it is not a
 * Sentinel authentication proof and must never unlock privileged wearable capabilities.
 */
object WearOsPlatformProbeRegistry {
    private data class PendingProbe(
        val nonce: ByteArray,
        val expiresAtMs: Long
    )

    private val random = SecureRandom()
    private val pending = ConcurrentHashMap<String, PendingProbe>()

    fun issue(rawNodeId: String, nowMs: Long = System.currentTimeMillis()): ByteArray? {
        val nodeId = rawNodeId.trim()
        if (nodeId.isEmpty() || nodeId.length > MAX_NODE_ID_CHARS || nowMs < 0L) return null

        removeExpired(nowMs)
        if (!pending.containsKey(nodeId) && pending.size >= MAX_PENDING_PROBES) return null

        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        pending[nodeId] = PendingProbe(
            nonce = nonce.copyOf(),
            expiresAtMs = nowMs + PROBE_TTL_MS
        )
        return nonce
    }

    fun acceptPong(
        rawNodeId: String,
        payload: ByteArray,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val nodeId = rawNodeId.trim()
        if (nodeId.isEmpty() || nodeId.length > MAX_NODE_ID_CHARS || nowMs < 0L) return false
        if (payload.size != NONCE_BYTES) return false

        val probe = pending.remove(nodeId) ?: return false
        if (nowMs >= probe.expiresAtMs) return false
        return MessageDigest.isEqual(probe.nonce, payload)
    }

    fun cancel(rawNodeId: String) {
        pending.remove(rawNodeId.trim())
    }

    private fun removeExpired(nowMs: Long) {
        pending.entries.removeIf { nowMs >= it.value.expiresAtMs }
    }

    internal fun clearForTests() {
        pending.clear()
    }

    internal const val NONCE_BYTES = 24
    internal const val PROBE_TTL_MS = 15_000L
    private const val MAX_PENDING_PROBES = 16
    private const val MAX_NODE_ID_CHARS = 512
}
