package com.sentinel.quantum.security

/**
 * Bounded outbound BLE frame queue. No Android radio APIs, encryption or authentication here.
 * Release defaults to fail-closed until a Noise session verifier is integrated.\n * The injected verifier is for deterministic test harnesses, not a cryptographic implementation.
 * Exactly one frame can be in flight; callbacks must match both generation and ticket.
 */
class OfflineMeshGattOutbox(
    private val maxFrames: Int = 32,
    private val maxFrameBytes: Int = 244,
    private val verifyAuthenticatedSession: (Long, ByteArray) -> Boolean = { _, _ -> false }
) {
    init { require(maxFrames in 1..1024 && maxFrameBytes in 1..4096) }
    enum class Offer { QUEUED, FULL, INVALID_FRAME, LOCKED }
    data class Pending(val ticket: Long, val generation: Long, val bytes: ByteArray)
    private val waiting = ArrayDeque<Pending>()
    private var inFlight: Pending? = null
    private var generation = 0L
    private var serial = 0L
    private var authorized = false
    private var sessionGeneration: Long? = null

    fun closeLink() {
        generation++
        authorized = false
        sessionGeneration = null
        waiting.clear()
        inFlight = null
    }

    /**
     * Requires proof for the current link generation. By default, verification
     * fails until the production Noise authenticator is supplied.
     * Never call from an untrusted discovery announcement.
     */
    fun openAuthenticatedLink(expectedGeneration: Long, authenticationProof: ByteArray): Boolean {
        if (expectedGeneration != generation || authenticationProof.isEmpty()) return false
        if (!verifyAuthenticatedSession(expectedGeneration, authenticationProof.copyOf())) return false
        waiting.clear()
        inFlight = null
        authorized = true
        sessionGeneration = expectedGeneration
        return true
    }

    fun offer(ciphertext: ByteArray): Offer {
        if (!authorized || sessionGeneration != generation) return Offer.LOCKED
        if (ciphertext.isEmpty() || ciphertext.size > maxFrameBytes) return Offer.INVALID_FRAME
        if (waiting.size + (if (inFlight == null) 0 else 1) >= maxFrames) return Offer.FULL
        serial++
        waiting.addLast(Pending(serial, generation, ciphertext.copyOf()))
        return Offer.QUEUED
    }

    /** A copied frame, safe for the radio adapter to mutate. */
    fun next(): Pending? {
        if (!authorized || inFlight != null) return null
        val item = waiting.removeFirstOrNull() ?: return null
        inFlight = item
        return item.copy(bytes = item.bytes.copyOf())
    }

    /** On failure invalidate entire link, avoiding stale or reordered transmission. */
    fun complete(ticket: Long, callbackGeneration: Long, success: Boolean): Boolean {
        val item = inFlight ?: return false
        if (!authorized || item.ticket != ticket || item.generation != callbackGeneration) return false
        if (!success) {
            closeLink()
            return false
        }
        inFlight = null
        return true
    }

    fun queuedCount(): Int = waiting.size + (if (inFlight != null) 1 else 0)
    fun currentGeneration(): Long = generation
}
