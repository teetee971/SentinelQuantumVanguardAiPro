package com.sentinel.quantum.security

/** In-memory, bounded reassembly. Caller must serialize access. */
class OfflineMeshGattReassembler(
    private val fragmentTimeoutMs: Long = 30_000L,
    private val maxMessageBytes: Int = 64 * 1024,
    private val maxActiveMessages: Int = 8
) {
    init { require(fragmentTimeoutMs > 0 && maxMessageBytes > 0 && maxActiveMessages > 0) }
    data class Result(
        val accepted: Boolean,
        val duplicate: Boolean = false,
        val complete: ByteArray? = null,
        val ack: OfflineMeshGattFrameCodec.Frame.Ack? = null
    )
    private data class Assembly(
        val count: Int,
        val created: Long,
        val pieces: MutableMap<Int, ByteArray> = mutableMapOf(),
        var size: Int = 0
    )
    private val active = mutableMapOf<Long, Assembly>()
    private val delivered = mutableMapOf<Long, Long>()

    @Synchronized fun accept(frame: OfflineMeshGattFrameCodec.Frame.Data, nowMs: Long): Result {
        expire(nowMs)
        if (frame.messageId < 0 || frame.fragmentCount !in 1..4096 ||
            frame.fragmentIndex !in 0 until frame.fragmentCount ||
            frame.payload.isEmpty() || frame.payload.size > 226) return Result(false)
        val ack = OfflineMeshGattFrameCodec.Frame.Ack(frame.messageId, frame.fragmentIndex)
        if (delivered.containsKey(frame.messageId)) return Result(true, duplicate = true, ack = ack)
        var assembly = active[frame.messageId]
        if (assembly == null) {
            if (active.size >= maxActiveMessages) return Result(false)
            assembly = Assembly(frame.fragmentCount, nowMs)
            active[frame.messageId] = assembly
        }
        if (assembly.count != frame.fragmentCount) {
            active.remove(frame.messageId)
            return Result(false)
        }
        val previous = assembly.pieces[frame.fragmentIndex]
        if (previous != null) {
            if (!previous.contentEquals(frame.payload)) {
                active.remove(frame.messageId)
                return Result(false)
            }
            return Result(true, duplicate = true, ack = ack)
        }
        if (frame.payload.size > maxMessageBytes - assembly.size) {
            active.remove(frame.messageId)
            return Result(false)
        }
        assembly.pieces[frame.fragmentIndex] = frame.payload.copyOf()
        assembly.size += frame.payload.size
        if (assembly.pieces.size != assembly.count) return Result(true, ack = ack)
        val bytes = ByteArray(assembly.size)
        var offset = 0
        for (i in 0 until assembly.count) {
            val part = assembly.pieces[i] ?: return Result(false)
            part.copyInto(bytes, offset)
            offset += part.size
        }
        active.remove(frame.messageId)
        delivered[frame.messageId] = nowMs
        return Result(true, complete = bytes, ack = ack)
    }

    @Synchronized fun expire(nowMs: Long): List<Long> {
        val expired = active.filterValues { nowMs >= it.created && nowMs - it.created > fragmentTimeoutMs }.keys.toList()
        expired.forEach(active::remove)
        delivered.entries.removeAll { nowMs >= it.value && nowMs - it.value > fragmentTimeoutMs }
        return expired
    }

    @Synchronized fun reset() {
        active.clear()
        delivered.clear()
    }
}
