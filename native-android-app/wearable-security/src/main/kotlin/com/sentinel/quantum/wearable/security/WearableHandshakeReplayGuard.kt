package com.sentinel.quantum.wearable.security

import java.security.SecureRandom
import java.util.Base64

data class WearableHandshakeChallenge(
    val nonce: String,
    val stableId: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long
)

data class WearableUnboundHandshakeChallenge(
    val nonce: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long
)

class WearableHandshakeReplayGuard(
    private val ttlMs: Long = 300_000L,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val elapsedClockMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private data class PendingChallenge(
        val nonce: String,
        val expectedStableId: String?,
        val issuedAtMs: Long,
        val expiresAtMs: Long,
        val issuedElapsedMs: Long
    )

    private val pending = mutableMapOf<String, PendingChallenge>()

    init { require(ttlMs in 1_000L..900_000L) }

    @Synchronized
    fun issue(stableId: String): WearableHandshakeChallenge {
        require(stableId.isNotBlank())
        val pendingChallenge = issueInternal(expectedStableId = stableId)
        return WearableHandshakeChallenge(
            nonce = pendingChallenge.nonce,
            stableId = stableId,
            issuedAtMs = pendingChallenge.issuedAtMs,
            expiresAtMs = pendingChallenge.expiresAtMs
        )
    }

    /** First-pairing challenge: identity is supplied and signed by the wearable response. */
    @Synchronized
    fun issueUnbound(): WearableUnboundHandshakeChallenge {
        val pendingChallenge = issueInternal(expectedStableId = null)
        return WearableUnboundHandshakeChallenge(
            nonce = pendingChallenge.nonce,
            issuedAtMs = pendingChallenge.issuedAtMs,
            expiresAtMs = pendingChallenge.expiresAtMs
        )
    }

    private fun issueInternal(expectedStableId: String?): PendingChallenge {
        val now = clockMs()
        val elapsedNow = elapsedClockMs()
        purgeExpired(now, elapsedNow)
        val expiresAt = Math.addExact(now, ttlMs)
        val bytes = ByteArray(32).also(random::nextBytes)
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return PendingChallenge(
            nonce = nonce,
            expectedStableId = expectedStableId,
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            issuedElapsedMs = elapsedNow
        ).also { pending[nonce] = it }
    }

    @Synchronized
    fun validate(candidate: WearableHandshakeCandidate): Boolean {
        val now = clockMs()
        val elapsedNow = elapsedClockMs()
        purgeExpired(now, elapsedNow)
        return candidateMatchesPending(candidate, now, elapsedNow)
    }

    @Synchronized
    fun consume(candidate: WearableHandshakeCandidate): Boolean {
        val now = clockMs()
        val elapsedNow = elapsedClockMs()
        purgeExpired(now, elapsedNow)
        if (!candidateMatchesPending(candidate, now, elapsedNow)) return false
        pending.remove(candidate.challengeNonce)
        return true
    }

    @Synchronized
    fun revokeAll() = pending.clear()

    private fun candidateMatchesPending(
        candidate: WearableHandshakeCandidate,
        now: Long,
        elapsedNow: Long
    ): Boolean {
        val challenge = pending[candidate.challengeNonce] ?: return false
        val elapsedAge = elapsedNow - challenge.issuedElapsedMs
        return (challenge.expectedStableId == null || challenge.expectedStableId == candidate.stableId) &&
            candidate.issuedAtMs >= challenge.issuedAtMs &&
            candidate.issuedAtMs <= challenge.expiresAtMs &&
            now >= challenge.issuedAtMs &&
            now <= challenge.expiresAtMs &&
            elapsedAge >= 0 && elapsedAge <= ttlMs
    }

    private fun purgeExpired(now: Long, elapsedNow: Long) {
        pending.entries.removeIf {
            val elapsedAge = elapsedNow - it.value.issuedElapsedMs
            now < it.value.issuedAtMs || now > it.value.expiresAtMs ||
                elapsedAge < 0 || elapsedAge > ttlMs
        }
    }
}
