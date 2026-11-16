package com.sentinel.quantum.security

/**
 * Pure domain guard for wearable application messages.
 *
 * Cryptographic authentication remains an adapter responsibility. This policy is applied only
 * after authentication succeeds and prevents stale-session, replayed, duplicated and unsupported
 * protocol messages from reaching Sentinel capability handlers.
 *
 * One guard belongs to one authorized peer/key epoch. Its bounded retired-session ledger is
 * never evicted: [WearableMessageDecision.REKEY_REQUIRED] leaves the current session usable
 * but refuses another rotation. Recreating the guard is safe only after all previous channels
 * are closed, their authority is revoked and fresh session keys are negotiated.
 */
data class WearableSession(
    val sessionId: String,
    val protocolVersion: Int,
    val negotiatedCapabilities: Set<WearableCapability>,
    val establishedAtMs: Long
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(protocolVersion > 0) { "protocolVersion must be positive" }
        require(establishedAtMs >= 0) { "establishedAtMs must be non-negative" }
    }
}

data class WearableMessageEnvelope(
    val sessionId: String,
    val protocolVersion: Int,
    val sequenceNumber: Long,
    val sentAtMs: Long,
    val requiredCapability: WearableCapability?
)

enum class WearableMessageDecision {
    ACCEPT,
    WRONG_SESSION,
    UNSUPPORTED_PROTOCOL,
    INVALID_SEQUENCE,
    DUPLICATE_OR_REPLAY,
    INVALID_TIMESTAMP,
    FUTURE_TIMESTAMP,
    EXPIRED,
    CAPABILITY_NOT_NEGOTIATED,
    REKEY_REQUIRED
}

class WearableMessageGuard(
    private val maxMessageAgeMs: Long = 30_000L,
    private val maxFutureSkewMs: Long = 5_000L,
    private val maxRetiredSessions: Int = 128
) {
    init {
        require(maxMessageAgeMs > 0) { "maxMessageAgeMs must be positive" }
        require(maxFutureSkewMs >= 0) { "maxFutureSkewMs must be non-negative" }
        require(maxRetiredSessions > 0) { "maxRetiredSessions must be positive" }
    }

    private var acceptedSessionId: String? = null
    private var highestAcceptedSequence: Long = -1
    private val retiredSessionIds = mutableSetOf<String>()

    @Synchronized
    fun evaluate(session: WearableSession, envelope: WearableMessageEnvelope, nowMs: Long): WearableMessageDecision {
        if (envelope.sessionId != session.sessionId) return WearableMessageDecision.WRONG_SESSION
        // A delayed callback may retain an older, formerly authoritative session snapshot.
        // Switching to a fresh session must permanently retire that older channel here.
        if (session.sessionId in retiredSessionIds) return WearableMessageDecision.WRONG_SESSION
        if (envelope.protocolVersion != session.protocolVersion) return WearableMessageDecision.UNSUPPORTED_PROTOCOL
        if (envelope.sequenceNumber < 0) return WearableMessageDecision.INVALID_SEQUENCE
        if (nowMs < 0 || envelope.sentAtMs < 0) return WearableMessageDecision.INVALID_TIMESTAMP
        // Both timestamps are non-negative, so ordered differences cannot overflow.
        if (envelope.sentAtMs > nowMs && envelope.sentAtMs - nowMs > maxFutureSkewMs) {
            return WearableMessageDecision.FUTURE_TIMESTAMP
        }
        if (nowMs >= envelope.sentAtMs && nowMs - envelope.sentAtMs > maxMessageAgeMs) {
            return WearableMessageDecision.EXPIRED
        }
        if (envelope.requiredCapability != null &&
            envelope.requiredCapability !in session.negotiatedCapabilities
        ) {
            return WearableMessageDecision.CAPABILITY_NOT_NEGOTIATED
        }

        if (acceptedSessionId != session.sessionId) {
            val previousSessionId = acceptedSessionId
            if (previousSessionId != null) {
                // Never evict replay tombstones to make space. The existing channel remains
                // usable; another rotation requires closing all old channels, revoking their
                // authority and negotiating fresh session keys before constructing a new guard.
                if (retiredSessionIds.size >= maxRetiredSessions) return WearableMessageDecision.REKEY_REQUIRED
                retiredSessionIds.add(previousSessionId)
            }
            acceptedSessionId = session.sessionId
            highestAcceptedSequence = -1
        }
        if (envelope.sequenceNumber <= highestAcceptedSequence) {
            return WearableMessageDecision.DUPLICATE_OR_REPLAY
        }

        highestAcceptedSequence = envelope.sequenceNumber
        return WearableMessageDecision.ACCEPT
    }
}
