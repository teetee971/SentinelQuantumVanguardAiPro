package com.sentinel.quantum.wearable.security

import java.security.SecureRandom
import java.util.Base64

data class WearableHandshakeChallenge(
    val nonce: String,
    val stableId: String,
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
        val challenge: WearableHandshakeChallenge,
        val issuedElapsedMs: Long
    )

    private val pending = mutableMapOf<String, PendingChallenge>()

    init { require(ttlMs in 1_000L..900_000L) }

    @Synchronized
    fun issue(stableId: String): WearableHandshakeChallenge {
        require(stableId.isNotBlank())
        val now = clockMs()
        val elapsedNow = elapsedClockMs()
        purgeExpired(now, elapsedNow)
        val expiresAt = Math.addExact(now, ttlMs)
        val bytes = ByteArray(32).also(random::nextBytes)
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return WearableHandshakeChallenge(nonce, stableId, now, expiresAt)
            .also { pending[nonce] = PendingChallenge(it, elapsedNow) }
    }

    @Synchronized
    fun consume(candidate: WearableHandshakeCandidate): Boolean {
        val now = clockMs()
        purgeExpired(now, elapsedClockMs())
        val challenge = pending[candidate.challengeNonce]?.challenge ?: return false
        if (challenge.stableId != candidate.stableId ||
            candidate.issuedAtMs < challenge.issuedAtMs ||
            candidate.issuedAtMs > challenge.expiresAtMs ||
            now > challenge.expiresAtMs
        ) return false
        pending.remove(candidate.challengeNonce)
        return true
    }

    @Synchronized
    fun revokeAll() = pending.clear()

    private fun purgeExpired(now: Long, elapsedNow: Long) {
        pending.entries.removeIf {
            val elapsedAge = elapsedNow - it.value.issuedElapsedMs
            // Wall time authenticates the transcript; monotonic time bounds nonce lifetime.
            // Clock rollback invalidates the challenge instead of extending its replay window.
            now < it.value.challenge.issuedAtMs || now > it.value.challenge.expiresAtMs ||
                elapsedAge < 0 || elapsedAge > ttlMs
        }
    }
}
