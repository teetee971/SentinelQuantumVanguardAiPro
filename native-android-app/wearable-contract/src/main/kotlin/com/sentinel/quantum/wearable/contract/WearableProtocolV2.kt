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
    private const val LIVENESS_NONCE_BYTES = 16
    private const val LIVENESS_DOMAIN = "sentinel-wearable-liveness-v2"
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

    enum class PeerRole { PHONE, WEARABLE }
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

    data class LivenessPayload(
        val sessionId: String,
        val sequenceNumber: Long,
        val sentAtMs: Long,
        val nonce: ByteArray
    ) {
        init {
            require(sessionId.isNotBlank())
            require(sequenceNumber >= 0)
            require(sentAtMs >= 0)
            require(nonce.size == LIVENESS_NONCE_BYTES)
        }
    }

    fun isSecurePath(path: String): Boolean = path == CHALLENGE_PATH ||
        path == HANDSHAKE_PATH || path == LIVENESS_PATH || path == MESSAGE_PATH

    fun encodeFrame(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_FRAME_BYTES - HEADER_BYTES) { "wearable secure frame too large" }
        return ByteBuffer.allocate(HEADER_BYTES + payload.size)
            .putInt(SCHEMA_VERSION).putInt(payload.size).put(payload).array()
    }

    fun decodeFrame(payload: ByteArray): Frame? {
        if (payload.size < HEADER_BYTES || payload.size > MAX_FRAME_BYTES) return null
        val buffer = ByteBuffer.wrap(payload)
        if (buffer.int != SCHEMA_VERSION) return null
        val size = buffer.int
        if (size < 0 || size > MAX_FRAME_BYTES - HEADER_BYTES || size != buffer.remaining()) return null
        return Frame(ByteArray(size).also(buffer::get))
    }

    fun encodeChallenge(value: ChallengePayload): ByteArray = encodeFrame(
        binary {
            writeUtf8(it, value.sessionId, MAX_SESSION_ID_BYTES)
            require(value.capabilities.isNotEmpty() && value.capabilities.size <= MAX_CAPABILITIES)
            val capabilities = value.capabilities.toSortedSet()
            it.writeInt(capabilities.size)
            capabilities.forEach { capability ->
                require(capability.isNotBlank())
                writeUtf8(it, capability, MAX_CAPABILITY_BYTES)
            }
            writeUtf8(it, value.challengeNonce, MAX_NONCE_BYTES)
            require(value.issuedAtMs >= 0)
            it.writeLong(value.issuedAtMs)
            writeBytes(it, value.phoneHpkePublicKeyEncoded, MAX_HPKE_PUBLIC_KEY_BYTES)
            require(value.phoneHpkePublicKeyFingerprintSha256.matches(SHA256_HEX))
            writeUtf8(it, value.phoneHpkePublicKeyFingerprintSha256, 64)
        }
    )

    fun decodeChallenge(encodedFrame: ByteArray): ChallengePayload? =
        decodeFrame(encodedFrame)?.let(::decodeChallengeFrame)

    fun decodeChallengeFrame(frame: Frame): ChallengePayload? = parse(frame.payload) { input ->
        val sessionId = readUtf8(input, MAX_SESSION_ID_BYTES)
        val count = input.readInt()
        require(count in 1..MAX_CAPABILITIES)
        val capabilities = LinkedHashSet<String>(count)
        repeat(count) {
            val capability = readUtf8(input, MAX_CAPABILITY_BYTES)
            require(capability.isNotBlank() && capabilities.add(capability))
        }
        val nonce = readUtf8(input, MAX_NONCE_BYTES)
        val issuedAt = input.readLong().also { require(it >= 0) }
        val phonePublic = readBytes(input, MAX_HPKE_PUBLIC_KEY_BYTES)
        val phoneFingerprint = readUtf8(input, 64).also { require(it.matches(SHA256_HEX)) }
        ChallengePayload(sessionId, capabilities, nonce, issuedAt, phonePublic, phoneFingerprint)
    }

    fun encodeHandshake(value: HandshakePayload): ByteArray = encodeFrame(
        binary {
            require(value.stableId.isNotBlank())
            writeUtf8(it, value.stableId, MAX_STABLE_ID_BYTES)
            writeBytes(it, value.identityPublicKeyX509, MAX_IDENTITY_PUBLIC_KEY_BYTES)
            require(value.identityKeyFingerprintSha256.matches(SHA256_HEX))
            writeUtf8(it, value.identityKeyFingerprintSha256, 64)
            writeBytes(it, value.wearableHpkePublicKeyEncoded, MAX_HPKE_PUBLIC_KEY_BYTES)
            require(value.wearableHpkePublicKeyFingerprintSha256.matches(SHA256_HEX))
            writeUtf8(it, value.wearableHpkePublicKeyFingerprintSha256, 64)
            writeBytes(it, value.signedTranscript, MAX_TRANSCRIPT_BYTES)
            writeBytes(it, value.signature, MAX_SIGNATURE_BYTES)
        }
    )

    fun decodeHandshake(encodedFrame: ByteArray): HandshakePayload? =
        decodeFrame(encodedFrame)?.let(::decodeHandshakeFrame)

    fun decodeHandshakeFrame(frame: Frame): HandshakePayload? = parse(frame.payload) { input ->
        val stableId = readUtf8(input, MAX_STABLE_ID_BYTES).also { require(it.isNotBlank()) }
        val identityPublic = readBytes(input, MAX_IDENTITY_PUBLIC_KEY_BYTES)
        val identityFingerprint = readUtf8(input, 64).also { require(it.matches(SHA256_HEX)) }
        val wearablePublic = readBytes(input, MAX_HPKE_PUBLIC_KEY_BYTES)
        val wearableFingerprint = readUtf8(input, 64).also { require(it.matches(SHA256_HEX)) }
        val transcript = readBytes(input, MAX_TRANSCRIPT_BYTES)
        val signature = readBytes(input, MAX_SIGNATURE_BYTES)
        HandshakePayload(
            stableId, identityPublic, identityFingerprint, wearablePublic,
            wearableFingerprint, transcript, signature
        )
    }

    fun encodeLivenessPlaintext(value: LivenessPayload): ByteArray = binary {
        writeUtf8(it, value.sessionId, MAX_SESSION_ID_BYTES)
        it.writeLong(value.sequenceNumber)
        it.writeLong(value.sentAtMs)
        writeBytes(it, value.nonce, LIVENESS_NONCE_BYTES)
    }

    fun decodeLivenessPlaintext(payload: ByteArray): LivenessPayload? {
        if (payload.isEmpty() || payload.size > 1024) return null
        return parse(payload) { input ->
            val sessionId = readUtf8(input, MAX_SESSION_ID_BYTES)
            val sequence = input.readLong().also { require(it >= 0) }
            val sentAt = input.readLong().also { require(it >= 0) }
            val nonce = readBytes(input, LIVENESS_NONCE_BYTES)
            require(nonce.size == LIVENESS_NONCE_BYTES)
            LivenessPayload(sessionId, sequence, sentAt, nonce)
        }
    }

    fun livenessContextInfo(
        sessionId: String,
        sender: PeerRole,
        recipient: PeerRole
    ): ByteArray {
        require(sender != recipient)
        return binary {
            writeUtf8(it, LIVENESS_DOMAIN, 64)
            it.writeInt(SCHEMA_VERSION)
            writeUtf8(it, sessionId, MAX_SESSION_ID_BYTES)
            writeUtf8(it, sender.name, 16)
            writeUtf8(it, recipient.name, 16)
        }
    }

    private inline fun binary(block: (DataOutputStream) -> Unit): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use(block)
            buffer.toByteArray()
        }

    private inline fun <T> parse(bytes: ByteArray, block: (DataInputStream) -> T): T? = runCatching {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val value = block(input)
            require(input.available() == 0)
            value
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
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun strictUtf8(value: String): ByteArray {
        val buffer = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }
}
