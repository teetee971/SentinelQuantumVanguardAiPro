package com.sentinel.quantum.security

/** Bounded, versioned GATT application frames. No authentication is implied by decoding. */
object OfflineMeshGattFrameCodec {
    private const val HEADER = 18
    private const val ACK_SIZE = 14
    private const val CONTROL_HEADER = 7
    private const val MAX_FRAME = 244
    private const val MAX_PAYLOAD = 64 * 1024
    private const val MAX_FRAGMENTS = 4096

    sealed class Frame {
        data class Data(val messageId: Long, val fragmentIndex: Int, val fragmentCount: Int, val payload: ByteArray) : Frame()
        data class Ack(val messageId: Long, val fragmentIndex: Int) : Frame()
        data class Control(val code: Int, val payload: ByteArray) : Frame()
    }

    fun encode(frame: Frame): ByteArray {
        val size = when (frame) {
            is Frame.Data -> {
                require(frame.messageId >= 0 && frame.fragmentCount in 1..MAX_FRAGMENTS &&
                    frame.fragmentIndex in 0 until frame.fragmentCount && frame.payload.isNotEmpty() &&
                    frame.payload.size <= MAX_FRAME - HEADER)
                HEADER + frame.payload.size
            }
            is Frame.Ack -> {
                require(frame.messageId >= 0 && frame.fragmentIndex in 0 until MAX_FRAGMENTS)
                ACK_SIZE
            }
            is Frame.Control -> {
                require(frame.code in 1..0xff && frame.payload.isNotEmpty() &&
                    frame.payload.size <= MAX_FRAME - CONTROL_HEADER)
                CONTROL_HEADER + frame.payload.size
            }
        }
        val out = java.nio.ByteBuffer.allocate(size).order(java.nio.ByteOrder.BIG_ENDIAN)
        out.put(0x53.toByte()).put(0x4d.toByte()).put(1.toByte())
        when (frame) {
            is Frame.Data -> {
                out.put(1.toByte()).putLong(frame.messageId)
                out.putShort(frame.fragmentIndex.toShort()).putShort(frame.fragmentCount.toShort())
                out.putShort(frame.payload.size.toShort()).put(frame.payload.copyOf())
            }
            is Frame.Ack -> out.put(2.toByte()).putLong(frame.messageId).putShort(frame.fragmentIndex.toShort())
            is Frame.Control -> out.put(3.toByte()).put(frame.code.toByte())
                .putShort(frame.payload.size.toShort()).put(frame.payload.copyOf())
        }
        return out.array()
    }

    fun decode(bytes: ByteArray, maxFrameBytes: Int = MAX_FRAME): Frame? {
        if (maxFrameBytes !in CONTROL_HEADER..MAX_FRAME || bytes.size > maxFrameBytes || bytes.size < CONTROL_HEADER) return null
        val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.BIG_ENDIAN)
        if (b.get().toInt() != 0x53 || b.get().toInt() != 0x4d || b.get().toInt() != 1) return null
        val type = b.get().toInt()
        return when (type) {
            1 -> {
                if (bytes.size < HEADER) return null
                val id = b.long
                if (id < 0) return null
                val index = b.short.toInt() and 0xffff
                val count = b.short.toInt() and 0xffff
                val length = b.short.toInt() and 0xffff
                if (count !in 1..MAX_FRAGMENTS || index >= count ||
                    length == 0 || length != bytes.size - HEADER) return null
                val payload = ByteArray(length)
                b.get(payload)
                Frame.Data(id, index, count, payload)
            }
            2 -> {
                if (bytes.size != ACK_SIZE) return null
                val id = b.long
                if (id < 0) return null
                val index = b.short.toInt() and 0xffff
                if (index < MAX_FRAGMENTS) Frame.Ack(id, index) else null
            }
            3 -> {
                val code = b.get().toInt() and 0xff
                val length = b.short.toInt() and 0xffff
                if (code == 0 || length == 0 || length != bytes.size - CONTROL_HEADER) return null
                val payload = ByteArray(length)
                b.get(payload)
                Frame.Control(code, payload)
            }
            else -> null
        }
    }

    fun fragment(messageId: Long, payload: ByteArray, maxFrameBytes: Int = MAX_FRAME): List<ByteArray> {
        require(messageId >= 0 && payload.isNotEmpty() && payload.size <= MAX_PAYLOAD)
        require(maxFrameBytes in (HEADER + 1)..MAX_FRAME)
        val chunk = maxFrameBytes - HEADER
        val count = (payload.size + chunk - 1) / chunk
        require(count in 1..MAX_FRAGMENTS)
        return (0 until count).map { i ->
            val start = i * chunk
            encode(Frame.Data(messageId, i, count, payload.copyOfRange(start, minOf(start + chunk, payload.size))))
        }
    }
}
