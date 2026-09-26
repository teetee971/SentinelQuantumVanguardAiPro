package com.sentinel.quantum.wearable.security

import java.security.SecureRandom
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableHandshakeReplayGuardTest {
    private var now = 100_000L
    private fun guard() = WearableHandshakeReplayGuard(
        ttlMs = 5_000L,
        clockMs = { now },
        random = SecureRandom()
    )

    private fun candidate(challenge: WearableHandshakeChallenge, stableId: String = challenge.stableId, issuedAtMs: Long = challenge.issuedAtMs) =
        WearableHandshakeCandidate(
            stableId = stableId,
            keyFingerprintSha256 = "a".repeat(64),
            sessionId = "session-1",
            protocolVersion = 1,
            capabilities = setOf("SENTINEL_ALERTS"),
            challengeNonce = challenge.nonce,
            issuedAtMs = issuedAtMs,
            signedTranscript = byteArrayOf(1),
            signature = byteArrayOf(2)
        )

    @Test fun consumesChallengeExactlyOnce() {
        val guard = guard()
        val challenge = guard.issue("watch-1")
        assertTrue(guard.consume(candidate(challenge)))
        assertFalse(guard.consume(candidate(challenge)))
    }

    @Test fun rejectsExpiredChallenge() {
        val guard = guard()
        val challenge = guard.issue("watch-1")
        now = challenge.expiresAtMs + 1
        assertFalse(guard.consume(candidate(challenge)))
    }

    @Test fun rejectsDifferentIdentityWithoutConsumingChallenge() {
        val guard = guard()
        val challenge = guard.issue("watch-1")
        assertFalse(guard.consume(candidate(challenge, stableId = "watch-2")))
        assertTrue(guard.consume(candidate(challenge)))
    }

    @Test fun rejectsUnknownNonce() {
        val guard = guard()
        val challenge = guard.issue("watch-1")
        val unknown = candidate(challenge).copy(challengeNonce = "BBBBBBBBBBBBBBBBBBBBBB")
        assertFalse(guard.consume(unknown))
    }

    @Test fun rejectsTimestampOutsideIssuedChallengeWindow() {
        val guard = guard()
        val challenge = guard.issue("watch-1")
        assertFalse(guard.consume(candidate(challenge, issuedAtMs = challenge.issuedAtMs - 1)))
        assertFalse(guard.consume(candidate(challenge, issuedAtMs = challenge.expiresAtMs + 1)))
        assertTrue(guard.consume(candidate(challenge)))
    }
}
