package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class WearableMessageGuardTest {
    private val now = 100_000L
    private val session = WearableSession(
        sessionId = "session-current",
        protocolVersion = 1,
        negotiatedCapabilities = setOf(WearableCapability.SENTINEL_ALERTS),
        establishedAtMs = 90_000L
    )

    private fun envelope(
        sessionId: String = session.sessionId,
        protocol: Int = 1,
        sequence: Long = 1,
        sentAt: Long = now - 1,
        capability: WearableCapability? = WearableCapability.SENTINEL_ALERTS
    ) = WearableMessageEnvelope(sessionId, protocol, sequence, sentAt, capability)

    @Test fun acceptsFreshMessageForCurrentSessionAndNegotiatedCapability() {
        assertEquals(WearableMessageDecision.ACCEPT, WearableMessageGuard().evaluate(session, envelope(), now))
    }

    @Test fun rejectsMessageFromPreviousSession() {
        assertEquals(
            WearableMessageDecision.WRONG_SESSION,
            WearableMessageGuard().evaluate(session, envelope(sessionId = "session-old"), now)
        )
    }

    @Test fun rejectsDuplicateAndLowerSequenceAsReplay() {
        val guard = WearableMessageGuard()
        assertEquals(WearableMessageDecision.ACCEPT, guard.evaluate(session, envelope(sequence = 4), now))
        assertEquals(WearableMessageDecision.DUPLICATE_OR_REPLAY, guard.evaluate(session, envelope(sequence = 4), now))
        assertEquals(WearableMessageDecision.DUPLICATE_OR_REPLAY, guard.evaluate(session, envelope(sequence = 3), now))
    }

    @Test fun newSessionResetsReplayWindowWithoutAcceptingOldSessionMessages() {
        val guard = WearableMessageGuard()
        assertEquals(WearableMessageDecision.ACCEPT, guard.evaluate(session, envelope(sequence = 8), now))
        val next = session.copy(sessionId = "session-next", establishedAtMs = now)
        assertEquals(
            WearableMessageDecision.ACCEPT,
            guard.evaluate(next, envelope(sessionId = "session-next", sequence = 0), now)
        )
        assertEquals(
            WearableMessageDecision.WRONG_SESSION,
            guard.evaluate(next, envelope(sessionId = "session-current", sequence = 9), now)
        )
    }

    @Test fun rejectsExpiredAndFarFutureMessages() {
        val guard = WearableMessageGuard(maxMessageAgeMs = 30_000, maxFutureSkewMs = 5_000)
        assertEquals(
            WearableMessageDecision.EXPIRED,
            guard.evaluate(session, envelope(sentAt = now - 30_001), now)
        )
        assertEquals(
            WearableMessageDecision.FUTURE_TIMESTAMP,
            guard.evaluate(session, envelope(sequence = 2, sentAt = now + 5_001), now)
        )
    }

    @Test fun rejectsUnsupportedProtocolAndUnnegotiatedCapability() {
        val guard = WearableMessageGuard()
        assertEquals(
            WearableMessageDecision.UNSUPPORTED_PROTOCOL,
            guard.evaluate(session, envelope(protocol = 2), now)
        )
        assertEquals(
            WearableMessageDecision.CAPABILITY_NOT_NEGOTIATED,
            guard.evaluate(session, envelope(capability = WearableCapability.QUICK_ACTIONS), now)
        )
    }

    @Test fun rejectedMessageDoesNotAdvanceReplayWindow() {
        val guard = WearableMessageGuard()
        assertEquals(
            WearableMessageDecision.CAPABILITY_NOT_NEGOTIATED,
            guard.evaluate(session, envelope(sequence = 9, capability = WearableCapability.QUICK_ACTIONS), now)
        )
        assertEquals(
            WearableMessageDecision.ACCEPT,
            guard.evaluate(session, envelope(sequence = 9), now)
        )
    }
}
