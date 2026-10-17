package com.sentinel.quantum.wear

internal class WearableWatchSecureSessionRegistry(
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    private data class Key(val nodeId: String, val sessionId: String)
    private data class Entry(val session: WearableHpkeDuplexSession, val expiresAtMs: Long)

    private val sessions = LinkedHashMap<Key, Entry>()

    init {
        require(maxSessions in 1..64)
        require(ttlMs in 1_000L..300_000L)
    }

    @Synchronized
    fun register(nodeId: String, sessionId: String, session: WearableHpkeDuplexSession): Boolean {
        if (nodeId.isBlank() || sessionId.isBlank()) return false
        val now = clockMs()
        if (now < 0) return false
        purgeExpired(now)
        val key = Key(nodeId, sessionId)
        if (key !in sessions && sessions.size >= maxSessions) return false
        val expiresAt = try {
            Math.addExact(now, ttlMs)
        } catch (_: ArithmeticException) {
            return false
        }
        sessions[key] = Entry(session, expiresAt)
        return true
    }

    @Synchronized
    fun resolve(nodeId: String, sessionId: String): WearableHpkeDuplexSession? {
        val now = clockMs()
        if (now < 0) return null
        purgeExpired(now)
        return sessions[Key(nodeId, sessionId)]?.session
    }

    @Synchronized
    fun revokeNode(nodeId: String) {
        sessions.keys.removeAll { it.nodeId == nodeId }
    }

    @Synchronized
    fun clear() {
        sessions.clear()
    }

    private fun purgeExpired(nowMs: Long) {
        sessions.entries.removeAll { nowMs >= it.value.expiresAtMs }
    }

    companion object {
        private const val DEFAULT_MAX_SESSIONS = 8
        private const val DEFAULT_TTL_MS = 120_000L
    }
}

internal object SentinelWearSecureSessions {
    val registry = WearableWatchSecureSessionRegistry()
}
