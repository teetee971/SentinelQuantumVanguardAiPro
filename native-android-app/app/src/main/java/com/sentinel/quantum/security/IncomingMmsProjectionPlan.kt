package com.sentinel.quantum.security

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Pure trust boundary between a validated M-Retrieve.conf and Android provider mutation.
 *
 * Nothing is synthesized here: an incoming provider row requires an explicit sender and at least
 * one protocol correlation identifier. Unsupported text encoding or unsafe metadata remains in the
 * private/quarantine path instead of being projected as a normal Android MMS.
 */
internal object IncomingMmsProjectionPlan {
    sealed interface Part {
        val mimeType: String
        val sizeBytes: Int

        data class Text(
            override val mimeType: String,
            override val sizeBytes: Int,
            val text: String
        ) : Part

        data class Binary(
            override val mimeType: String,
            override val sizeBytes: Int,
            val payload: ByteArray
        ) : Part
    }

    data class Plan(
        val digestHex: String,
        val sender: String,
        val messageId: String?,
        val transactionId: String?,
        val dateSeconds: Long,
        val mmsVersion: Int,
        val contentType: String,
        val subscriptionId: Int,
        val messageSizeBytes: Long,
        val textOnly: Boolean,
        val parts: List<Part>
    )

    sealed interface Result {
        data class Ready(val plan: Plan) : Result
        data class Quarantined(val reason: String) : Result
    }

    fun build(
        digestHex: String,
        envelope: MmsRetrieveEnvelopeParser.Envelope,
        safeParts: List<MmsDecodeBoundary.SafePart>,
        subscriptionId: Int
    ): Result {
        val digest = digestHex.lowercase().takeIf { DIGEST.matches(it) }
            ?: return Result.Quarantined("INVALID_PRIVATE_IDENTITY")
        if (subscriptionId < 0) return Result.Quarantined("INVALID_SUBSCRIPTION")
        if (envelope.messageType != MESSAGE_TYPE_RETRIEVE_CONF) {
            return Result.Quarantined("NOT_RETRIEVE_CONF")
        }
        if (envelope.dateSeconds < 0L) return Result.Quarantined("INVALID_DATE")
        if (envelope.senderDisposition != MmsRetrieveEnvelopeParser.SenderDisposition.ADDRESS) {
            return Result.Quarantined("SENDER_NOT_EXPLICIT")
        }
        val sender = envelope.senderAddress
            ?.trim()
            ?.takeIf(::isSaneSender)
            ?: return Result.Quarantined("INVALID_SENDER")
        val messageId = envelope.messageId?.takeIf(::isSaneProtocolId)
        val transactionId = envelope.transactionId?.takeIf(::isSaneProtocolId)
        if (messageId == null && transactionId == null) {
            return Result.Quarantined("MISSING_RECOVERY_IDENTITY")
        }
        if (envelope.messageId != null && messageId == null) {
            return Result.Quarantined("INVALID_MESSAGE_ID")
        }
        if (envelope.transactionId != null && transactionId == null) {
            return Result.Quarantined("INVALID_TRANSACTION_ID")
        }
        if (envelope.contentType !in MULTIPART_TYPES) {
            return Result.Quarantined("UNSUPPORTED_CONTENT_TYPE")
        }
        // The current safety decoder deliberately strips SMIL and per-part relation metadata.
        // Projecting multipart/related or multipart/alternative after that loss would manufacture a
        // canonical provider representation that is not faithful to the carrier PDU. Keep those
        // messages private until Content-ID/Content-Location/SMIL preservation is implemented.
        if (envelope.contentType !in PROVIDER_PROJECTABLE_TYPES) {
            return Result.Quarantined("PRESENTATION_METADATA_NOT_PRESERVED")
        }
        if (safeParts.isEmpty() || safeParts.size > MAX_PARTS) {
            return Result.Quarantined("INVALID_PART_COUNT")
        }

        var total = 0L
        val projected = ArrayList<Part>(safeParts.size)
        for (part in safeParts) {
            val size = part.payload.size
            if (size <= 0 || size > MAX_PART_BYTES) {
                return Result.Quarantined("INVALID_PART_SIZE")
            }
            total += size.toLong()
            if (total > MAX_TOTAL_BYTES) return Result.Quarantined("MESSAGE_TOO_LARGE")
            when (part.mimeType) {
                "text/plain" -> {
                    val text = decodeUtf8Strict(part.payload)
                        ?: return Result.Quarantined("TEXT_CHARSET_UNSUPPORTED")
                    projected += Part.Text(part.mimeType, size, text)
                }
                "image/jpeg", "image/png", "image/gif", "image/webp" -> {
                    projected += Part.Binary(part.mimeType, size, part.payload.copyOf())
                }
                else -> return Result.Quarantined("UNSUPPORTED_SAFE_PART")
            }
        }

        return Result.Ready(
            Plan(
                digestHex = digest,
                sender = sender,
                messageId = messageId,
                transactionId = transactionId,
                dateSeconds = envelope.dateSeconds,
                mmsVersion = envelope.mmsVersion,
                contentType = envelope.contentType,
                subscriptionId = subscriptionId,
                messageSizeBytes = total,
                textOnly = projected.all { it is Part.Text },
                parts = projected
            )
        )
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String? = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    private fun isSaneSender(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= MAX_SENDER_CHARS &&
            value.all { it.code in 0x20..0x7e } &&
            value.none { it == '/' || it == '\u0000' }

    private fun isSaneProtocolId(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= MAX_PROTOCOL_ID_CHARS &&
            value.all { it.code in 0x21..0x7e }

    private const val MESSAGE_TYPE_RETRIEVE_CONF = 0x84
    private const val MAX_SENDER_CHARS = 256
    private const val MAX_PROTOCOL_ID_CHARS = 256
    private const val MAX_PARTS = 32
    private const val MAX_PART_BYTES = 8 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 16L * 1024L * 1024L
    private val DIGEST = Regex("^[0-9a-f]{64}$")
    private val MULTIPART_TYPES = setOf(
        "application/vnd.wap.multipart.mixed",
        "application/vnd.wap.multipart.alternative",
        "application/vnd.wap.multipart.related"
    )
    private val PROVIDER_PROJECTABLE_TYPES = setOf(
        "application/vnd.wap.multipart.mixed"
    )
}
