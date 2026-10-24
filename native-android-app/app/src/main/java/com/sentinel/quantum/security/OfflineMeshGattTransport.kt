package com.sentinel.quantum.security

fun interface OfflineMeshGattAuthenticator {
    fun authenticate(generation: Long, proof: ByteArray): Boolean
}

/** Stop-and-wait GATT application transport. Authentication is denied by default. */
class OfflineMeshGattTransport(
    private val maxFrames: Int = 32,
    private val maxFrameBytes: Int = 244,
    private val ackTimeoutMs: Long = 1_500L,
    private val maxRetries: Int = 3,
    private val authenticator: OfflineMeshGattAuthenticator = OfflineMeshGattAuthenticator { _, _ -> false }
) {
    init {
        require(maxFrames in 1..4096 && maxFrameBytes in 19..244 && ackTimeoutMs > 0 && maxRetries >= 1)
    }
    enum class State { LOCKED, AUTHENTICATED }
    enum class Offer { LOCKED, QUEUED, FULL, INVALID }
    data class Outbound(
        val bytes: ByteArray,
        val ticket: Long,
        val generation: Long,
        val fragmentIndex: Int,
        val attempts: Int,
        val requiresAck: Boolean
    )
    sealed class Tick {
        data class Retry(val outbound: Outbound) : Tick()
        object Interrupted : Tick()
        object Idle : Tick()
    }
    data class Inbound(val accepted: Boolean, val complete: ByteArray? = null)

    private var generation: Long = -1
    private var status = State.LOCKED
    private var nextMessageId = 1L
    private var nextTicket = 1L
    private val dataQueue = ArrayDeque<Pair<Long, ByteArray>>()
    private val ackQueue = ArrayDeque<ByteArray>()
    private val reassembler = OfflineMeshGattReassembler()
    private var pending: Outbound? = null
    private var radioPending = false
    private var deadline = Long.MAX_VALUE

    @Synchronized fun state(): State = status
    @Synchronized fun queuedCount(): Int = dataQueue.size + (if (pending != null) 1 else 0)

    @Synchronized fun startLink(newGeneration: Long): Boolean {
        if (newGeneration < 0 || newGeneration <= generation) return false
        reset()
        generation = newGeneration
        return true
    }
    @Synchronized fun openAuthenticatedLink(linkGeneration: Long, proof: ByteArray): Boolean {
        if (linkGeneration != generation || status != State.LOCKED ||
            !authenticator.authenticate(linkGeneration, proof.copyOf())) return false
        status = State.AUTHENTICATED
        return true
    }
    @Synchronized fun offer(payload: ByteArray): Offer {
        if (status != State.AUTHENTICATED) return Offer.LOCKED
        if (payload.isEmpty() || payload.size > 64 * 1024) return Offer.INVALID
        val frames = try {
            OfflineMeshGattFrameCodec.fragment(nextMessageId, payload, maxFrameBytes)
        } catch (_: IllegalArgumentException) {
            return Offer.INVALID
        }
        if (frames.size > maxFrames || dataQueue.size + frames.size + (if (pending?.requiresAck == true) 1 else 0) > maxFrames)
            return Offer.FULL
        val id = nextMessageId++
        frames.forEach { dataQueue.addLast(id to it) }
        return Offer.QUEUED
    }
    @Synchronized fun next(nowMs: Long): Outbound? {
        if (status != State.AUTHENTICATED || radioPending) return null
        // Application ACK is mandatory before advancing to the next data fragment.
        if (ackQueue.isNotEmpty()) {
            val bytes = ackQueue.removeFirst()
            val frame = OfflineMeshGattFrameCodec.decode(bytes) as? OfflineMeshGattFrameCodec.Frame.Ack
                ?: return null
            val out = Outbound(bytes.copyOf(), nextTicket++, generation, frame.fragmentIndex, 1, false)
            radioPending = true
            if (pending == null) pending = out
            return out
        }
        if (pending != null) return null
        val (_, bytes) = dataQueue.removeFirstOrNull() ?: return null
        val frame = OfflineMeshGattFrameCodec.decode(bytes, maxFrameBytes) as? OfflineMeshGattFrameCodec.Frame.Data
            ?: return null
        return Outbound(bytes.copyOf(), nextTicket++, generation, frame.fragmentIndex, 1, true).also {
            pending = it
            radioPending = true
        }
    }
    @Synchronized fun onRadioWriteComplete(ticket: Long, linkGeneration: Long, success: Boolean, nowMs: Long): Boolean {
        if (status != State.AUTHENTICATED || linkGeneration != generation || !radioPending) return false
        val item = pending ?: return false
        if (item.ticket != ticket) return false
        if (!success) { reset(); return false }
        radioPending = false
        if (!item.requiresAck) {
            pending = null
        } else {
            deadline = nowMs + ackTimeoutMs
        }
        return true
    }
    @Synchronized fun receive(bytes: ByteArray, nowMs: Long): Inbound {
        if (status != State.AUTHENTICATED) return Inbound(false)
        val frame = OfflineMeshGattFrameCodec.decode(bytes, maxFrameBytes) ?: return Inbound(false)
        return when (frame) {
            is OfflineMeshGattFrameCodec.Frame.Data -> {
                val result = reassembler.accept(frame, nowMs)
                if (result.accepted && result.ack != null) {
                    if (ackQueue.size >= maxFrames) { reset(); return Inbound(false) }
                    ackQueue.addLast(OfflineMeshGattFrameCodec.encode(result.ack))
                }
                Inbound(result.accepted, result.complete?.copyOf())
            }
            is OfflineMeshGattFrameCodec.Frame.Ack -> {
                val waiting = pending
                val expected = waiting?.bytes?.let {
                    OfflineMeshGattFrameCodec.decode(it, maxFrameBytes) as? OfflineMeshGattFrameCodec.Frame.Data
                }
                if (waiting == null || !waiting.requiresAck || radioPending ||
                    expected == null || expected.messageId != frame.messageId ||
                    expected.fragmentIndex != frame.fragmentIndex) Inbound(false)
                else {
                    pending = null
                    deadline = Long.MAX_VALUE
                    Inbound(true)
                }
            }
        }
    }
    @Synchronized fun tick(nowMs: Long): Tick {
        val item = pending ?: return Tick.Idle
        if (status != State.AUTHENTICATED || radioPending || !item.requiresAck || nowMs < deadline)
            return Tick.Idle
        if (item.attempts >= maxRetries) {
            reset()
            return Tick.Interrupted
        }
        val retry = item.copy(ticket = nextTicket++, attempts = item.attempts + 1)
        pending = retry
        radioPending = true
        return Tick.Retry(retry)
    }
    @Synchronized fun interrupt() = reset()

    private fun reset() {
        status = State.LOCKED
        dataQueue.clear()
        ackQueue.clear()
        pending = null
        radioPending = false
        deadline = Long.MAX_VALUE
        reassembler.reset()
    }
}
