package com.sentinel.quantum.security

import java.io.ByteArrayOutputStream

/**
 * Small, dependency-free encoder for the bounded M-Send.req subset Sentinel supports.
 *
 * This component only composes bytes. It performs no I/O and never submits a message to telephony.
 * Inputs must already have passed [MmsSendEligibilityPolicy].
 */
object SentinelMmsSendPduComposer {
    data class Part(
        val mimeType: String,
        val payload: ByteArray
    )

    sealed class Result {
        data class Composed(val pdu: ByteArray) : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun compose(
        destination: String,
        transactionId: String,
        text: String,
        attachments: List<Part>
    ): Result {
        if (destination.isBlank() || destination.length > 32) return Result.Rejected("INVALID_DESTINATION")
        if (!destination.all { it.isDigit() || it in "+*#" }) return Result.Rejected("INVALID_DESTINATION")
        if (transactionId.isBlank() || transactionId.length > 40 ||
            !transactionId.all { it.code in 0x21..0x7e }) return Result.Rejected("INVALID_TRANSACTION_ID")
        if (text.length > MmsSendEligibilityPolicy.MAX_TEXT_CHARS) return Result.Rejected("TEXT_TOO_LARGE")
        if (attachments.size > MmsSendEligibilityPolicy.MAX_ATTACHMENTS) return Result.Rejected("TOO_MANY_ATTACHMENTS")

        val parts = ArrayList<Part>(attachments.size + 1)
        if (text.isNotEmpty()) parts += Part("text/plain", text.toByteArray(Charsets.UTF_8))
        parts += attachments
        if (parts.isEmpty()) return Result.Rejected("EMPTY_MMS")

        var total = 0L
        for (part in parts) {
            if (part.mimeType !in SUPPORTED_MIME_TYPES) return Result.Rejected("UNSUPPORTED_MIME")
            if (part.payload.isEmpty() || part.payload.size.toLong() > MmsSendEligibilityPolicy.MAX_ATTACHMENT_BYTES) {
                return Result.Rejected("INVALID_PART_SIZE")
            }
            total += part.payload.size.toLong()
            if (total > MmsSendEligibilityPolicy.MAX_TOTAL_ATTACHMENT_BYTES) return Result.Rejected("MMS_TOO_LARGE")
        }

        return runCatching {
            val out = ByteArrayOutputStream()
            out.write(X_MMS_MESSAGE_TYPE)
            out.write(M_SEND_REQ)
            out.write(X_MMS_TRANSACTION_ID)
            writeTextString(out, transactionId)
            out.write(X_MMS_VERSION)
            out.write(MMS_VERSION_1_2)
            out.write(FROM)
            out.write(1)
            out.write(INSERT_ADDRESS_TOKEN)
            out.write(TO)
            writeTextString(out, destination)
            out.write(CONTENT_TYPE)
            out.write(MULTIPART_MIXED)
            writeUintvar(out, parts.size)
            parts.forEach { part ->
                val header = encodedContentType(part.mimeType)
                writeUintvar(out, header.size)
                writeUintvar(out, part.payload.size)
                out.write(header)
                out.write(part.payload)
            }
            Result.Composed(out.toByteArray())
        }.getOrElse { Result.Rejected("PDU_COMPOSITION_FAILED") }
    }

    private fun encodedContentType(mimeType: String): ByteArray =
        WELL_KNOWN_CONTENT_TYPES[mimeType]?.let { byteArrayOf(it.toByte()) }
            ?: (mimeType.toByteArray(Charsets.US_ASCII) + byteArrayOf(0))

    private fun writeTextString(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        require(bytes.none { it == 0.toByte() })
        if (bytes.firstOrNull()?.toInt()?.and(0xff)?.let { it >= 0x80 } == true) out.write(0x7f)
        out.write(bytes)
        out.write(0)
    }

    internal fun encodeUintvar(value: Int): ByteArray {
        require(value >= 0)
        val stack = IntArray(5)
        var size = 0
        var remaining = value
        do {
            stack[size++] = remaining and 0x7f
            remaining = remaining ushr 7
        } while (remaining > 0)
        return ByteArray(size) { index ->
            val reverse = size - 1 - index
            val continuation = if (reverse != 0) 0x80 else 0
            (stack[reverse] or continuation).toByte()
        }
    }

    private fun writeUintvar(out: ByteArrayOutputStream, value: Int) {
        out.write(encodeUintvar(value))
    }

    private val SUPPORTED_MIME_TYPES = setOf(
        "text/plain", "image/jpeg", "image/png", "image/gif", "image/webp"
    )

    private val WELL_KNOWN_CONTENT_TYPES = mapOf(
        "text/plain" to 0x83,
        "image/gif" to 0x9d,
        "image/jpeg" to 0x9e,
        "image/png" to 0x9f
    )

    private const val X_MMS_MESSAGE_TYPE = 0x8c
    private const val M_SEND_REQ = 0x80
    private const val X_MMS_TRANSACTION_ID = 0x98
    private const val X_MMS_VERSION = 0x8d
    private const val MMS_VERSION_1_2 = 0x92
    private const val FROM = 0x89
    private const val INSERT_ADDRESS_TOKEN = 0x81
    private const val TO = 0x97
    private const val CONTENT_TYPE = 0x84
    private const val MULTIPART_MIXED = 0xa3
}
