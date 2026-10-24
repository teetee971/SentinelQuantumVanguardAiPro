package com.sentinel.quantum.security

/**
 * Bounded outbound BLE frame queue. No Android radio APIs, encryption or authentication here.
 * Caller must prove authenticated link before invoking openAuthenticatedLink().
 * Exactly one frame can be in flight; callbacks must match both generation and ticket.
 */
class OfflineMeshGattOutbox(
    private val maxFrames: Int = 32,
    private val maxFrameBytes: Int = 244
) {
    init { require(maxFrames in 1..1024 && maxFrameBytes in 1..4096) }
    enum class Offer { QUEUED, FULL, INVALID_FRAME, LOCKED }
    data class Pending(val ticket: Long, val generation: Long, val bytes: ByteArray)
    private val waiting = ArrayDeque<Pending>()
    private var inFlight: Pending? = null
    private var generation = 0L
    private var serial = 0L
    private var authorized = false

    fun closeLink() {
        generation++
        authorized = false
        waiting.clear()
        inFlight = null
    }

    /**
     * ONLY call after Noise authentication is verified on the current transport generation.
     * This class does not verify that proof itself.
     */
    fun openAuthenticatedLink() {
        closeLink()
        authorized = true
    }

    fun offer(ciphertext: ByteArray): Offer {
        if (!authorized) return Offer.LOCKED
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
