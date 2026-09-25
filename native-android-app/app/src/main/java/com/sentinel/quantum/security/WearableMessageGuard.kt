package com.sentinel.quantum.security

/**
 * Pure domain guard for wearable application messages.
 *
 * Cryptographic authentication remains an adapter responsibility. This policy is applied only
 * after authentication succeeds and prevents stale-session, replayed, duplicated and unsupported
 * protocol messages from reaching Sentinel capability handlers.
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
    FUTURE_TIMESTAMP,
    EXPIRED,
    CAPABILITY_NOT_NEGOTIATED
}

class WearableMessageGuard(
    private val maxMessageAgeMs: Long = 30_000L,
    private val maxFutureSkewMs: Long = 5_000L
) {
    init {
        require(maxMessageAgeMs > 0) { "maxMessageAgeMs must be positive" }
        require(maxFutureSkewMs >= 0) { "maxFutureSkewMs must be non-negative" }
    }

    private var acceptedSessionId: String? = null
    private var highestAcceptedSequence: Long = -1

    @Synchronized
    fun evaluate(session: WearableSession, envelope: WearableMessageEnvelope, nowMs: Long): WearableMessageDecision {
        if (envelope.sessionId != session.sessionId) return WearableMessageDecision.WRONG_SESSION
        if (envelope.protocolVersion != session.protocolVersion) return WearableMessageDecision.UNSUPPORTED_PROTOCOL
        if (envelope.sequenceNumber < 0) return WearableMessageDecision.INVALID_SEQUENCE
        if (envelope.sentAtMs > nowMs + maxFutureSkewMs) return WearableMessageDecision.FUTURE_TIMESTAMP
        if (nowMs < envelope.sentAtMs || nowMs - envelope.sentAtMs > maxMessageAgeMs) {
            return WearableMessageDecision.EXPIRED
        }
        if (envelope.requiredCapability != null &&
            envelope.requiredCapability !in session.negotiatedCapabilities
        ) {
            return WearableMessageDecision.CAPABILITY_NOT_NEGOTIATED
        }

        if (acceptedSessionId != session.sessionId) {
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
