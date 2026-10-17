package com.sentinel.quantum.wear

internal class WearableWatchSecureSessionRegistry(
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    data class ResolvedSession(
        val sessionId: String,
        val session: WearableHpkeDuplexSession
    )

    private data class Key(val nodeId: String, val sessionId: String)
    private data class Entry(
        val session: WearableHpkeDuplexSession,
        var expiresAtMs: Long,
        var highestAcceptedLivenessSequence: Long = -1L
    )

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
        val expiresAt = try {
            Math.addExact(now, ttlMs)
        } catch (_: ArithmeticException) {
            return false
        }
        purgeExpired(now)
        val existingForNode = sessions.keys.any { it.nodeId == nodeId }
        if (!existingForNode && sessions.size >= maxSessions) return false
        sessions.keys.removeAll { it.nodeId == nodeId }
        sessions[Key(nodeId, sessionId)] = Entry(session, expiresAt)
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
    fun resolveNode(nodeId: String): ResolvedSession? {
        val now = clockMs()
        if (now < 0) return null
        purgeExpired(now)
        val match = sessions.entries.firstOrNull { it.key.nodeId == nodeId } ?: return null
        return ResolvedSession(match.key.sessionId, match.value.session)
    }

    @Synchronized
    fun acceptLivenessSequence(nodeId: String, sessionId: String, sequenceNumber: Long): Boolean {
        if (sequenceNumber < 0) return false
        val now = clockMs()
        if (now < 0) return false
        purgeExpired(now)
        val entry = sessions[Key(nodeId, sessionId)] ?: return false
        if (sequenceNumber <= entry.highestAcceptedLivenessSequence) return false
        val refreshedExpiry = try {
            Math.addExact(now, ttlMs)
        } catch (_: ArithmeticException) {
            return false
        }
        entry.highestAcceptedLivenessSequence = sequenceNumber
        entry.expiresAtMs = refreshedExpiry
        return true
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
