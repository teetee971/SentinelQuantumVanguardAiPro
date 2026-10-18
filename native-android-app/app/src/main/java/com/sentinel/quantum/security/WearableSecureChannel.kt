package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.security.WearableHpkeKeyPair
import com.sentinel.quantum.wearable.security.WearableHpkePublicKey
import com.sentinel.quantum.wearable.security.WearableHpkeSender
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException

enum class WearablePeerRole { PHONE, WEARABLE }

enum class WearableSecureMessageState { ACCEPTED, REJECTED }

enum class WearableSecureMessageRejection {
    INVALID_DIRECTION,
    INVALID_OUTBOUND_ENVELOPE,
    CIPHERTEXT_BOUNDS,
    DECRYPTION_FAILED,
    PLAINTEXT_BOUNDS,
    MALFORMED_ENVELOPE,
    CONTEXT_MISMATCH,
    MESSAGE_GUARD_REJECTED
}

data class WearableSecureMessageResult(
    val state: WearableSecureMessageState,
    val payload: ByteArray? = null,
    val envelope: WearableMessageEnvelope? = null,
    val guardDecision: WearableMessageDecision? = null,
    val rejection: WearableSecureMessageRejection? = null
)

internal interface WearableSecureCryptoEndpoint {
    @Throws(GeneralSecurityException::class)
    fun encrypt(plaintext: ByteArray, contextInfo: ByteArray): ByteArray

    @Throws(GeneralSecurityException::class)
    fun decrypt(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray
}

class WearableSecureChannel internal constructor(
    private val session: WearableSession,
    private val localRole: WearablePeerRole,
    private val crypto: WearableSecureCryptoEndpoint,
    private val messageGuard: WearableMessageGuard = WearableMessageGuard()
) {
    fun seal(
        payload: ByteArray,
        envelope: WearableMessageEnvelope,
        senderRole: WearablePeerRole
    ): ByteArray {
        require(senderRole == localRole) { "sender role does not match local channel role" }
        require(payload.size <= MAX_PAYLOAD_BYTES) { "wearable payload exceeds maximum size" }
        require(outboundEnvelopeValid(envelope)) { "outbound wearable envelope is invalid" }
        val context = contextInfo(senderRole, opposite(senderRole), envelope.requiredCapability)
        val plaintext = encodeEnvelope(envelope, payload)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "wearable plaintext exceeds maximum size" }
        val ciphertext = crypto.encrypt(plaintext, context)
        require(ciphertext.isNotEmpty() && ciphertext.size <= MAX_CIPHERTEXT_BYTES) {
            "wearable ciphertext exceeds maximum size"
        }
        return ciphertext
    }

    fun open(
        ciphertext: ByteArray,
        expectedCapability: WearableCapability?,
        senderRole: WearablePeerRole,
        nowMs: Long
    ): WearableSecureMessageResult {
        if (senderRole == localRole) return rejected(WearableSecureMessageRejection.INVALID_DIRECTION)
        if (ciphertext.isEmpty() || ciphertext.size > MAX_CIPHERTEXT_BYTES) {
            return rejected(WearableSecureMessageRejection.CIPHERTEXT_BOUNDS)
        }
        val context = runCatching {
            contextInfo(senderRole, localRole, expectedCapability)
        }.getOrElse { return rejected(WearableSecureMessageRejection.CONTEXT_MISMATCH) }
        val plaintext = try {
            crypto.decrypt(ciphertext.copyOf(), context)
        } catch (_: GeneralSecurityException) {
            return rejected(WearableSecureMessageRejection.DECRYPTION_FAILED)
        } catch (_: IllegalArgumentException) {
            return rejected(WearableSecureMessageRejection.DECRYPTION_FAILED)
        }
        if (plaintext.isEmpty() || plaintext.size > MAX_PLAINTEXT_BYTES) {
            return rejected(WearableSecureMessageRejection.PLAINTEXT_BOUNDS)
        }
        val decoded = runCatching { decodeEnvelope(plaintext) }
            .getOrElse { return rejected(WearableSecureMessageRejection.MALFORMED_ENVELOPE) }
        if (decoded.envelope.requiredCapability != expectedCapability) {
            return rejected(WearableSecureMessageRejection.CONTEXT_MISMATCH)
        }
        val decision = messageGuard.evaluate(session, decoded.envelope, nowMs)
        if (decision != WearableMessageDecision.ACCEPT) {
            return WearableSecureMessageResult(
                state = WearableSecureMessageState.REJECTED,
                envelope = decoded.envelope,
                guardDecision = decision,
                rejection = WearableSecureMessageRejection.MESSAGE_GUARD_REJECTED
            )
        }
        return WearableSecureMessageResult(
            state = WearableSecureMessageState.ACCEPTED,
            payload = decoded.payload.copyOf(),
            envelope = decoded.envelope,
            guardDecision = decision
        )
    }

    private fun outboundEnvelopeValid(envelope: WearableMessageEnvelope): Boolean =
        envelope.sessionId == session.sessionId &&
            envelope.protocolVersion == session.protocolVersion &&
            envelope.sequenceNumber >= 0 &&
            envelope.sentAtMs >= 0 &&
            (envelope.requiredCapability == null ||
                envelope.requiredCapability in session.negotiatedCapabilities)

    private fun contextInfo(
        sender: WearablePeerRole,
        recipient: WearablePeerRole,
        capability: WearableCapability?
    ): ByteArray {
        require(sender != recipient)
        return ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                writeBoundedUtf8(out, CONTEXT_DOMAIN, 64)
                out.writeInt(session.protocolVersion)
                writeBoundedUtf8(out, session.sessionId, MAX_SESSION_ID_BYTES)
                writeBoundedUtf8(out, sender.name, 16)
                writeBoundedUtf8(out, recipient.name, 16)
                writeBoundedUtf8(out, capability?.name ?: NONE_CAPABILITY, 64)
            }
            buffer.toByteArray()
        }
    }

    private data class DecodedEnvelope(
        val envelope: WearableMessageEnvelope,
        val payload: ByteArray
    )

    private fun encodeEnvelope(envelope: WearableMessageEnvelope, payload: ByteArray): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(FRAME_MAGIC)
                out.writeInt(FRAME_VERSION)
                writeBoundedUtf8(out, envelope.sessionId, MAX_SESSION_ID_BYTES)
                out.writeInt(envelope.protocolVersion)
                out.writeLong(envelope.sequenceNumber)
                out.writeLong(envelope.sentAtMs)
                writeBoundedUtf8(out, envelope.requiredCapability?.name ?: NONE_CAPABILITY, 64)
                out.writeInt(payload.size)
                out.write(payload)
            }
            buffer.toByteArray()
        }

    private fun decodeEnvelope(bytes: ByteArray): DecodedEnvelope =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == FRAME_MAGIC)
            require(input.readInt() == FRAME_VERSION)
            val sessionId = readBoundedUtf8(input, MAX_SESSION_ID_BYTES)
            val protocolVersion = input.readInt()
            val sequence = input.readLong()
            val sentAt = input.readLong()
            val capabilityName = readBoundedUtf8(input, 64)
            val capability = if (capabilityName == NONE_CAPABILITY) null else
                WearableCapability.entries.firstOrNull { it.name == capabilityName }
                    ?: throw IllegalArgumentException("unknown wearable capability")
            val payloadSize = input.readInt()
            require(payloadSize in 0..MAX_PAYLOAD_BYTES)
            require(payloadSize <= input.available())
            val payload = ByteArray(payloadSize)
            input.readFully(payload)
            require(input.available() == 0) { "trailing wearable frame bytes" }
            DecodedEnvelope(
                WearableMessageEnvelope(
                    sessionId = sessionId,
                    protocolVersion = protocolVersion,
                    sequenceNumber = sequence,
                    sentAtMs = sentAt,
                    requiredCapability = capability
                ),
                payload
            )
        }

    private fun writeBoundedUtf8(out: DataOutputStream, value: String, maxBytes: Int) {
        val encoded = strictUtf8(value)
        require(encoded.size <= maxBytes)
        out.writeInt(encoded.size)
        out.write(encoded)
    }

    private fun readBoundedUtf8(input: DataInputStream, maxBytes: Int): String {
        val size = input.readInt()
        require(size in 0..maxBytes)
        require(size <= input.available())
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
            .encode(java.nio.CharBuffer.wrap(value))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun opposite(role: WearablePeerRole): WearablePeerRole = when (role) {
        WearablePeerRole.PHONE -> WearablePeerRole.WEARABLE
        WearablePeerRole.WEARABLE -> WearablePeerRole.PHONE
    }

    private fun rejected(reason: WearableSecureMessageRejection) =
        WearableSecureMessageResult(
            state = WearableSecureMessageState.REJECTED,
            rejection = reason
        )

    companion object {
        const val MAX_CIPHERTEXT_BYTES = 128 * 1024
        private const val MAX_PLAINTEXT_BYTES = 64 * 1024
        private const val MAX_PAYLOAD_BYTES = 60 * 1024
        private const val MAX_SESSION_ID_BYTES = 256
        private const val CONTEXT_DOMAIN = "sentinel-wearable-channel-v2"
        private const val NONE_CAPABILITY = "NONE"
        private const val FRAME_MAGIC = 0x53574D32 // SWM2
        private const val FRAME_VERSION = 2

        fun forPhone(
            session: WearableSession,
            phoneHpkeKeyPair: WearableHpkeKeyPair,
            wearableHpkePublicKey: WearableHpkePublicKey,
            messageGuard: WearableMessageGuard = WearableMessageGuard()
        ): WearableSecureChannel {
            val sender = WearableHpkeSender.fromPublicKey(wearableHpkePublicKey)
            return WearableSecureChannel(
                session = session,
                localRole = WearablePeerRole.PHONE,
                crypto = object : WearableSecureCryptoEndpoint {
                    override fun encrypt(plaintext: ByteArray, contextInfo: ByteArray): ByteArray =
                        sender.encrypt(plaintext, contextInfo)
                    override fun decrypt(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray =
                        phoneHpkeKeyPair.decrypt(ciphertext, contextInfo)
                },
                messageGuard = messageGuard
            )
        }
    }
}
