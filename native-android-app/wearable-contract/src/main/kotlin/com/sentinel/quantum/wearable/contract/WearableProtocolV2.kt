package com.sentinel.quantum.wearable.contract

import java.nio.ByteBuffer

object WearableProtocolV2 {
    const val SCHEMA_VERSION = 2
    const val SECURE_PREFIX = "/sentinel/v2/secure/"
    const val CHALLENGE_PATH = "/sentinel/v2/secure/challenge"
    const val HANDSHAKE_PATH = "/sentinel/v2/secure/handshake"
    const val LIVENESS_PATH = "/sentinel/v2/secure/liveness"
    const val MESSAGE_PATH = "/sentinel/v2/secure/message"
    const val MAX_FRAME_BYTES = 128 * 1024

    private const val HEADER_BYTES = 8

    data class Frame(val payload: ByteArray)

    fun isSecurePath(path: String): Boolean = path == CHALLENGE_PATH ||
        path == HANDSHAKE_PATH || path == LIVENESS_PATH || path == MESSAGE_PATH

    fun encodeFrame(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_FRAME_BYTES - HEADER_BYTES) { "wearable secure frame too large" }
        return ByteBuffer.allocate(HEADER_BYTES + payload.size)
            .putInt(SCHEMA_VERSION)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    fun decodeFrame(payload: ByteArray): Frame? {
        if (payload.size < HEADER_BYTES || payload.size > MAX_FRAME_BYTES) return null
        val buffer = ByteBuffer.wrap(payload)
        if (buffer.int != SCHEMA_VERSION) return null
        val size = buffer.int
        if (size < 0 || size > MAX_FRAME_BYTES - HEADER_BYTES || size != buffer.remaining()) return null
        val body = ByteArray(size)
        buffer.get(body)
        return Frame(body)
    }
}
