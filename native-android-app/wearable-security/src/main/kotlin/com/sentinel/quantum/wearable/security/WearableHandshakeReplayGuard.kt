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
    private val random: SecureRandom = SecureRandom()
) {
    private val pending = mutableMapOf<String, WearableHandshakeChallenge>()

    init { require(ttlMs in 1_000L..900_000L) }

    @Synchronized
    fun issue(stableId: String): WearableHandshakeChallenge {
        require(stableId.isNotBlank())
        val now = clockMs()
        purgeExpired(now)
        val bytes = ByteArray(32).also(random::nextBytes)
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return WearableHandshakeChallenge(nonce, stableId, now, now + ttlMs)
            .also { pending[nonce] = it }
    }

    @Synchronized
    fun consume(candidate: WearableHandshakeCandidate): Boolean {
        val now = clockMs()
        purgeExpired(now)
        val challenge = pending[candidate.challengeNonce] ?: return false
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

    private fun purgeExpired(now: Long) {
        pending.entries.removeIf { now > it.value.expiresAtMs }
    }
}
