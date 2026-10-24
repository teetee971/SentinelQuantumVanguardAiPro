package com.sentinel.quantum.security

import java.security.MessageDigest

/**
 * Authenticated wrapper around the bounded GATT transport. Handshake control
 * frames are exchanged before the data transport is unlocked; application
 * bytes are encrypted before fragmentation.
 */
class OfflineMeshGattSecureTransport(
    private val crypto: OfflineMeshGattCryptoSession?,
    maxFrames: Int = 32,
    maxFrameBytes: Int = 244,
    ackTimeoutMs: Long = 1_500L,
    maxRetries: Int = 3
) {
    enum class State { LOCKED, HANDSHAKING, AUTHENTICATED }

    data class Outbound(
        val bytes: ByteArray,
        val ticket: Long,
        val generation: Long,
        val fragmentIndex: Int,
        val attempts: Int,
        val requiresAck: Boolean,
        val isControl: Boolean
    )

    sealed class Tick {
        data class Retry(val outbound: Outbound) : Tick()
        object Interrupted : Tick()
        object Idle : Tick()
    }

    data class Inbound(val accepted: Boolean, val complete: ByteArray? = null)

    private val transport = OfflineMeshGattTransport(
        maxFrames = maxFrames,
        maxFrameBytes = maxFrameBytes,
        ackTimeoutMs = ackTimeoutMs,
        maxRetries = maxRetries,
        authenticator = OfflineMeshGattAuthenticator { _, proof ->
            val session = crypto
            session != null && session.isEstablished() &&
                MessageDigest.isEqual(proof, session.sessionProof())
        }
    )
    private val controlQueue = ArrayDeque<ByteArray>()
    private var controlPending: Outbound? = null
    private var activeGeneration = -1L
    private var nextControlTicket = 1L
    private var nextMessageCounter = 1L
    private var currentState = State.LOCKED

    fun state(): State = currentState
    fun isAuthenticated(): Boolean = currentState == State.AUTHENTICATED

    fun startLink(generation: Long): Boolean {
        if (!transport.startLink(generation)) return false
        crypto?.reset()
        controlQueue.clear()
        controlPending = null
        activeGeneration = generation
        nextMessageCounter = 1L
        currentState = if (crypto == null) State.LOCKED else State.HANDSHAKING
        if (crypto?.role == OfflineMeshGattCryptoSession.Role.INITIATOR) {
            crypto.start()?.let(controlQueue::addLast) ?: return false
        }
        return true
    }

    fun offer(payload: ByteArray): OfflineMeshGattTransport.Offer {
        if (!isAuthenticated()) return OfflineMeshGattTransport.Offer.LOCKED
        val session = crypto ?: return OfflineMeshGattTransport.Offer.LOCKED
        val counter = nextMessageCounter
        val encrypted = runCatching { session.encrypt(payload, counter) }.getOrNull()
            ?: return OfflineMeshGattTransport.Offer.INVALID
        val result = transport.offer(encrypted)
        if (result == OfflineMeshGattTransport.Offer.QUEUED) nextMessageCounter++
        return result
    }

    fun next(nowMs: Long): Outbound? {
        if (controlPending != null) return null
        if (controlQueue.isNotEmpty()) {
            val bytes = controlQueue.removeFirst()
            return Outbound(
                bytes = bytes.copyOf(),
                ticket = nextControlTicket++,
                generation = activeGeneration,
                fragmentIndex = 0,
                attempts = 1,
                requiresAck = false,
                isControl = true
            ).also { controlPending = it }
        }
        val inner = transport.next(nowMs) ?: return null
        return Outbound(
            bytes = inner.bytes.copyOf(),
            ticket = inner.ticket,
            generation = inner.generation,
            fragmentIndex = inner.fragmentIndex,
            attempts = inner.attempts,
            requiresAck = inner.requiresAck,
            isControl = false
        )
    }

    fun onRadioWriteComplete(
        ticket: Long,
        generation: Long,
        success: Boolean,
        nowMs: Long
    ): Boolean {
        if (generation != activeGeneration) return false
        val control = controlPending
        if (control != null) {
            if (control.ticket != ticket) return false
            controlPending = null
            if (!success) {
                interrupt()
                return false
            }
            return true
        }
        return transport.onRadioWriteComplete(ticket, generation, success, nowMs)
    }

    fun receive(bytes: ByteArray, nowMs: Long): Inbound {
        val frame = OfflineMeshGattFrameCodec.decode(bytes) ?: return Inbound(false)
        if (frame is OfflineMeshGattFrameCodec.Frame.Control) {
            val session = crypto ?: return Inbound(false)
            if (currentState != State.HANDSHAKING) return Inbound(false)
            val result = session.receive(bytes)
            if (!result.accepted) {
                interrupt()
                return Inbound(false)
            }
            result.outbound?.let(controlQueue::addLast)
            if (session.isEstablished()) {
                if (!transport.openAuthenticatedLink(activeGeneration, session.sessionProof())) {
                    interrupt()
                    return Inbound(false)
                }
                currentState = State.AUTHENTICATED
            }
            return Inbound(true)
        }
        if (!isAuthenticated()) return Inbound(false)
        val inbound = transport.receive(bytes, nowMs)
        if (!inbound.accepted || inbound.complete == null) return Inbound(inbound.accepted)
        val plaintext = crypto?.decrypt(inbound.complete) ?: return Inbound(false)
        return Inbound(true, plaintext)
    }

    fun tick(nowMs: Long): Tick {
        if (controlPending != null) return Tick.Idle
        return when (val tick = transport.tick(nowMs)) {
            is OfflineMeshGattTransport.Tick.Retry -> Tick.Retry(
                Outbound(
                    bytes = tick.outbound.bytes.copyOf(),
                    ticket = tick.outbound.ticket,
                    generation = tick.outbound.generation,
                    fragmentIndex = tick.outbound.fragmentIndex,
                    attempts = tick.outbound.attempts,
                    requiresAck = tick.outbound.requiresAck,
                    isControl = false
                )
            )
            OfflineMeshGattTransport.Tick.Interrupted -> {
                currentState = State.LOCKED
                Tick.Interrupted
            }
            OfflineMeshGattTransport.Tick.Idle -> Tick.Idle
        }
    }

    fun interrupt() {
        transport.interrupt()
        crypto?.reset()
        controlQueue.clear()
        controlPending = null
        currentState = State.LOCKED
    }
}
