package com.sentinel.quantum.security

import java.io.ByteArrayOutputStream
import java.io.OutputStream

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
        attachments: List<Part>,
        maxPduBytes: Int = Int.MAX_VALUE
    ): Result {
        val rejection = validate(destination, transactionId, text, attachments, maxPduBytes)
        if (rejection != null) return Result.Rejected(rejection)
        return runCatching {
            val parts = bodyParts(text, attachments)
            val counter = SizeOutput()
            writePdu(counter, destination, transactionId, parts)
            if (counter.count > maxPduBytes) return Result.Rejected("MMS_CARRIER_SIZE_EXCEEDED")
            val out = ByteArrayOutputStream(counter.count)
            writePdu(out, destination, transactionId, parts)
            val pdu = out.toByteArray()
            if (pdu.size > maxPduBytes) Result.Rejected("MMS_CARRIER_SIZE_EXCEEDED")
            else Result.Composed(pdu)
        }.getOrElse { Result.Rejected("PDU_COMPOSITION_FAILED") }
    }

    /** Uses the same encoder without allocating an oversized original PDU. No carrier gate is bypassed. */
    internal fun measuredSize(destination: String, transactionId: String, text: String, attachments: List<Part>): Int? {
        if (validate(destination, transactionId, text, attachments, Int.MAX_VALUE) != null) return null
        return runCatching {
            SizeOutput().apply { writePdu(this, destination, transactionId, bodyParts(text, attachments)) }.count
        }.getOrNull()
    }

    private fun validate(destination: String, transactionId: String, text: String, attachments: List<Part>, maxPduBytes: Int): String? {
        if (maxPduBytes <= 0) return "MMS_CARRIER_CONFIG_UNAVAILABLE"
        if (destination.isBlank() || destination.length > 32 || !destination.all { it.isDigit() || it in "+*#" }) return "INVALID_DESTINATION"
        if (transactionId.isBlank() || transactionId.length > 40 || !transactionId.all { it.code in 0x21..0x7e }) return "INVALID_TRANSACTION_ID"
        if (text.length > MmsOutgoingLimits.MAX_TEXT_CHARS) return "TEXT_TOO_LARGE"
        if (attachments.size > MmsOutgoingLimits.MAX_ATTACHMENTS) return "TOO_MANY_ATTACHMENTS"
        val parts = bodyParts(text, attachments)
        if (parts.isEmpty()) return "EMPTY_MMS"
        var total = 0L
        for (part in parts) {
            if (part.mimeType !in SUPPORTED_MIME_TYPES) return "UNSUPPORTED_MIME"
            if (part.payload.isEmpty() || part.payload.size.toLong() > MmsOutgoingLimits.MAX_ATTACHMENT_BYTES) return "INVALID_PART_SIZE"
            total += part.payload.size.toLong()
            if (total > MmsOutgoingLimits.MAX_TOTAL_ATTACHMENT_BYTES) return "MMS_TOO_LARGE"
        }
        return null
    }

    private fun bodyParts(text: String, attachments: List<Part>): List<Part> =
        (if (text.isNotEmpty()) listOf(Part("text/plain", text.toByteArray(Charsets.UTF_8))) else emptyList()) + attachments

    private fun writePdu(out: OutputStream, destination: String, transactionId: String, parts: List<Part>) {
        out.write(X_MMS_MESSAGE_TYPE); out.write(M_SEND_REQ); out.write(X_MMS_TRANSACTION_ID)
        writeTextString(out, transactionId)
        out.write(X_MMS_VERSION); out.write(MMS_VERSION_1_2); out.write(FROM); out.write(1)
        out.write(INSERT_ADDRESS_TOKEN); out.write(TO); writeTextString(out, destination)
        out.write(CONTENT_TYPE); out.write(MULTIPART_MIXED); writeUintvar(out, parts.size)
        parts.forEach { part ->
            val header = encodedContentType(part.mimeType)
            writeUintvar(out, header.size); writeUintvar(out, part.payload.size)
            out.write(header); out.write(part.payload)
        }
    }

    private class SizeOutput : OutputStream() {
        var count = 0; private set
        override fun write(value: Int) { count = Math.addExact(count, 1) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { count = Math.addExact(count, length) }
    }

    private fun encodedContentType(mimeType: String): ByteArray =
        WELL_KNOWN_CONTENT_TYPES[mimeType]?.let { byteArrayOf(it.toByte()) }
            ?: (mimeType.toByteArray(Charsets.US_ASCII) + byteArrayOf(0))

    private fun writeTextString(out: OutputStream, value: String) {
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

    private fun writeUintvar(out: OutputStream, value: Int) {
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
