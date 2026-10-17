package com.sentinel.quantum.wearable.contract

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object WearableProtocolV2 {
    const val SCHEMA_VERSION = 2
    const val SECURE_PREFIX = "/sentinel/v2/secure/"
    const val CHALLENGE_PATH = "/sentinel/v2/secure/challenge"
    const val HANDSHAKE_PATH = "/sentinel/v2/secure/handshake"
    const val LIVENESS_PATH = "/sentinel/v2/secure/liveness"
    const val MESSAGE_PATH = "/sentinel/v2/secure/message"
    const val MAX_FRAME_BYTES = 128 * 1024

    private const val HEADER_BYTES = 8
    private const val MAX_SESSION_ID_BYTES = 256
    private const val MAX_STABLE_ID_BYTES = 128
    private const val MAX_CAPABILITIES = 32
    private const val MAX_CAPABILITY_BYTES = 64
    private const val MAX_NONCE_BYTES = 128
    private const val MAX_HPKE_PUBLIC_KEY_BYTES = 4 * 1024
    private const val MAX_IDENTITY_PUBLIC_KEY_BYTES = 1024
    private const val MAX_TRANSCRIPT_BYTES = 16 * 1024
    private const val MAX_SIGNATURE_BYTES = 512
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

    data class Frame(val payload: ByteArray)

    data class ChallengePayload(
        val sessionId: String,
        val capabilities: Set<String>,
        val challengeNonce: String,
        val issuedAtMs: Long,
        val phoneHpkePublicKeyEncoded: ByteArray,
        val phoneHpkePublicKeyFingerprintSha256: String
    )

    data class HandshakePayload(
        val stableId: String,
        val identityPublicKeyX509: ByteArray,
        val identityKeyFingerprintSha256: String,
        val wearableHpkePublicKeyEncoded: ByteArray,
        val wearableHpkePublicKeyFingerprintSha256: String,
        val signedTranscript: ByteArray,
        val signature: ByteArray
    )

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

    fun encodeChallenge(value: ChallengePayload): ByteArray = encodeFrame(
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                writeUtf8(out, value.sessionId, MAX_SESSION_ID_BYTES)
                require(value.capabilities.isNotEmpty() && value.capabilities.size <= MAX_CAPABILITIES)
                val capabilities = value.capabilities.toSortedSet()
                out.writeInt(capabilities.size)
                capabilities.forEach { capability ->
                    require(capability.isNotBlank())
                    writeUtf8(out, capability, MAX_CAPABILITY_BYTES)
                }
                writeUtf8(out, value.challengeNonce, MAX_NONCE_BYTES)
                require(value.issuedAtMs >= 0)
                out.writeLong(value.issuedAtMs)
                writeBytes(out, value.phoneHpkePublicKeyEncoded, MAX_HPKE_PUBLIC_KEY_BYTES)
                require(value.phoneHpkePublicKeyFingerprintSha256.matches(SHA256_HEX))
                writeUtf8(out, value.phoneHpkePublicKeyFingerprintSha256, 64)
            }
            buffer.toByteArray()
        }
    )

    fun decodeChallenge(encodedFrame: ByteArray): ChallengePayload? =
        decodeFrame(encodedFrame)?.let(::decodeChallengeFrame)

    fun decodeChallengeFrame(frame: Frame): ChallengePayload? = runCatching {
        DataInputStream(ByteArrayInputStream(frame.payload)).use { input ->
            val sessionId = readUtf8(input, MAX_SESSION_ID_BYTES)
            val count = input.readInt()
            require(count in 1..MAX_CAPABILITIES)
            val capabilities = LinkedHashSet<String>(count)
            repeat(count) {
                val capability = readUtf8(input, MAX_CAPABILITY_BYTES)
                require(capability.isNotBlank() && capabilities.add(capability))
            }
            val nonce = readUtf8(input, MAX_NONCE_BYTES)
            val issuedAt = input.readLong()
            require(issuedAt >= 0)
            val phonePublic = readBytes(input, MAX_HPKE_PUBLIC_KEY_BYTES)
            val phoneFingerprint = readUtf8(input, 64)
            require(phoneFingerprint.matches(SHA256_HEX))
            require(input.available() == 0)
            ChallengePayload(
                sessionId,
                capabilities,
                nonce,
                issuedAt,
                phonePublic,
                phoneFingerprint
            )
        }
    }.getOrNull()

    fun encodeHandshake(value: HandshakePayload): ByteArray = encodeFrame(
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                require(value.stableId.isNotBlank())
                writeUtf8(out, value.stableId, MAX_STABLE_ID_BYTES)
                writeBytes(out, value.identityPublicKeyX509, MAX_IDENTITY_PUBLIC_KEY_BYTES)
                require(value.identityKeyFingerprintSha256.matches(SHA256_HEX))
                writeUtf8(out, value.identityKeyFingerprintSha256, 64)
                writeBytes(out, value.wearableHpkePublicKeyEncoded, MAX_HPKE_PUBLIC_KEY_BYTES)
                require(value.wearableHpkePublicKeyFingerprintSha256.matches(SHA256_HEX))
                writeUtf8(out, value.wearableHpkePublicKeyFingerprintSha256, 64)
                writeBytes(out, value.signedTranscript, MAX_TRANSCRIPT_BYTES)
                writeBytes(out, value.signature, MAX_SIGNATURE_BYTES)
            }
            buffer.toByteArray()
        }
    )

    fun decodeHandshake(encodedFrame: ByteArray): HandshakePayload? =
        decodeFrame(encodedFrame)?.let(::decodeHandshakeFrame)

    fun decodeHandshakeFrame(frame: Frame): HandshakePayload? = runCatching {
        DataInputStream(ByteArrayInputStream(frame.payload)).use { input ->
            val stableId = readUtf8(input, MAX_STABLE_ID_BYTES)
            require(stableId.isNotBlank())
            val identityPublic = readBytes(input, MAX_IDENTITY_PUBLIC_KEY_BYTES)
            val identityFingerprint = readUtf8(input, 64)
            require(identityFingerprint.matches(SHA256_HEX))
            val wearablePublic = readBytes(input, MAX_HPKE_PUBLIC_KEY_BYTES)
            val wearableFingerprint = readUtf8(input, 64)
            require(wearableFingerprint.matches(SHA256_HEX))
            val transcript = readBytes(input, MAX_TRANSCRIPT_BYTES)
            val signature = readBytes(input, MAX_SIGNATURE_BYTES)
            require(input.available() == 0)
            HandshakePayload(
                stableId,
                identityPublic,
                identityFingerprint,
                wearablePublic,
                wearableFingerprint,
                transcript,
                signature
            )
        }
    }.getOrNull()

    private fun writeBytes(out: DataOutputStream, value: ByteArray, maxBytes: Int) {
        require(value.isNotEmpty() && value.size <= maxBytes)
        out.writeInt(value.size)
        out.write(value)
    }

    private fun readBytes(input: DataInputStream, maxBytes: Int): ByteArray {
        val size = input.readInt()
        require(size in 1..maxBytes && size <= input.available())
        return ByteArray(size).also(input::readFully)
    }

    private fun writeUtf8(out: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = strictUtf8(value)
        require(bytes.isNotEmpty() && bytes.size <= maxBytes)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readUtf8(input: DataInputStream, maxBytes: Int): String {
        val size = input.readInt()
        require(size in 1..maxBytes && size <= input.available())
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }

    private fun strictUtf8(value: String): ByteArray {
        val buffer = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }
}
