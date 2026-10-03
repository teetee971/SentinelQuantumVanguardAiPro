package com.sentinel.quantum.security

/**
 * Bounded parser for the MMS headers needed before projecting an incoming M-Retrieve.conf into
 * Android's Telephony provider.
 *
 * This parser intentionally stops at the top-level Content-Type. The multipart body remains under
 * [SentinelMmsPduDecoder] + [MmsDecodeBoundary]. Header parsing and body safety are separate trust
 * boundaries: neither one makes the other safe.
 *
 * Unsupported/ambiguous headers fail closed. We do not scan arbitrary bytes for header markers and
 * we never synthesize a sender, message id or transaction id when the protocol omitted them.
 */
internal object MmsRetrieveEnvelopeParser {
    enum class SenderDisposition {
        ADDRESS,
        ABSENT,
        INSERT_ADDRESS_TOKEN
    }

    data class Envelope(
        val messageType: Int,
        val mmsVersion: Int,
        val dateSeconds: Long,
        val senderDisposition: SenderDisposition,
        val senderAddress: String?,
        val messageId: String?,
        val transactionId: String?,
        val contentType: String,
        val bodyOffset: Int
    )

    sealed interface Result {
        data class Accepted(val envelope: Envelope) : Result
        data class Rejected(val reason: String) : Result
    }

    fun parse(pdu: ByteArray): Result {
        if (pdu.isEmpty() || pdu.size > MAX_PDU_BYTES) return Result.Rejected("INVALID_PDU_SIZE")

        val cursor = Cursor(pdu, 0, pdu.size)
        var messageType: Int? = null
        var mmsVersion: Int? = null
        var dateSeconds: Long? = null
        var senderDisposition = SenderDisposition.ABSENT
        var senderAddress: String? = null
        var senderSeen = false
        var messageId: String? = null
        var transactionId: String? = null
        var contentType: String? = null
        var bodyOffset: Int? = null
        val singletons = HashSet<Int>()

        fun markSingleton(header: Int): Boolean = singletons.add(header)

        while (cursor.remaining > 0) {
            val header = cursor.read() ?: return Result.Rejected("TRUNCATED_HEADER")
            if (header < 0x80) {
                return Result.Rejected("APPLICATION_HEADER_UNSUPPORTED")
            }

            when (header) {
                HEADER_MESSAGE_TYPE -> {
                    if (!markSingleton(header)) return Result.Rejected("DUPLICATE_MESSAGE_TYPE")
                    messageType = cursor.read() ?: return Result.Rejected("TRUNCATED_MESSAGE_TYPE")
                }

                HEADER_MMS_VERSION -> {
                    if (!markSingleton(header)) return Result.Rejected("DUPLICATE_MMS_VERSION")
                    mmsVersion = readShortInteger(cursor)
                        ?: return Result.Rejected("INVALID_MMS_VERSION")
                }

                HEADER_DATE -> {
                    if (!markSingleton(header)) return Result.Rejected("DUPLICATE_DATE")
                    dateSeconds = readLongInteger(cursor)
                        ?: return Result.Rejected("INVALID_DATE")
                }

                HEADER_FROM -> {
                    if (!markSingleton(header)) return Result.Rejected("DUPLICATE_FROM")
                    senderSeen = true
                    when (val from = readFromValue(cursor)) {
                        is FromValue.Address -> {
                            senderDisposition = SenderDisposition.ADDRESS
                            senderAddress = from.value
                        }
                        FromValue.InsertAddress -> {
                            senderDisposition = SenderDisposition.INSERT_ADDRESS_TOKEN
                            senderAddress = null
                        }
                        null -> return Result.Rejected("INVALID_FROM")
                    }
                }

                HEADER_MESSAGE_ID -> {
                    if (!markSingleton(header)) return Result.Rejected("DUPLICATE_MESSAGE_ID")
                    messageId = readAsciiTextString(cursor, MAX_ID_CHARS)
                        ?: return Result.Rejected("INVALID_MESSAGE_ID")
                }

                HEADER_TRANSACTION_ID -> {
                    if (!markSingleton(header)) return Result.Rejected("DUPLICATE_TRANSACTION_ID")
                    transactionId = readAsciiTextString(cursor, MAX_ID_CHARS)
                        ?: return Result.Rejected("INVALID_TRANSACTION_ID")
                }

                HEADER_TO, HEADER_CC, HEADER_BCC -> {
                    if (!skipEncodedStringValue(cursor)) {
                        return Result.Rejected("INVALID_ADDRESSING_HEADER")
                    }
                }

                HEADER_SUBJECT, HEADER_RETRIEVE_TEXT, HEADER_RESPONSE_TEXT,
                HEADER_STATUS_TEXT, HEADER_STORE_STATUS_TEXT -> {
                    if (!skipEncodedStringValue(cursor)) {
                        return Result.Rejected("INVALID_ENCODED_STRING_HEADER")
                    }
                }

                HEADER_MESSAGE_CLASS -> {
                    if (!markSingleton(header)) return Result.Rejected("DUPLICATE_MESSAGE_CLASS")
                    if (!skipMessageClass(cursor)) return Result.Rejected("INVALID_MESSAGE_CLASS")
                }

                HEADER_PRIORITY, HEADER_DELIVERY_REPORT, HEADER_READ_REPLY,
                HEADER_REPORT_ALLOWED, HEADER_SENDER_VISIBILITY, HEADER_RETRIEVE_STATUS,
                HEADER_CONTENT_CLASS, HEADER_DRM_CONTENT, HEADER_ADAPTATION_ALLOWED -> {
                    if (!markSingleton(header)) return Result.Rejected("DUPLICATE_OCTET_HEADER")
                    if (cursor.read() == null) return Result.Rejected("TRUNCATED_OCTET_HEADER")
                }

                HEADER_CONTENT_TYPE -> {
                    if (!markSingleton(header)) return Result.Rejected("DUPLICATE_CONTENT_TYPE")
                    val parsed = readContentType(cursor)
                        ?: return Result.Rejected("INVALID_CONTENT_TYPE")
                    contentType = parsed
                    bodyOffset = cursor.position
                    break
                }

                else -> return Result.Rejected("UNSUPPORTED_HEADER_${header.toString(16).uppercase()}")
            }
        }

        val type = messageType ?: return Result.Rejected("MISSING_MESSAGE_TYPE")
        if (type != MESSAGE_TYPE_RETRIEVE_CONF) return Result.Rejected("NOT_RETRIEVE_CONF")

        val version = mmsVersion ?: return Result.Rejected("MISSING_MMS_VERSION")
        if (version !in SUPPORTED_MMS_VERSIONS) return Result.Rejected("UNSUPPORTED_MMS_VERSION")

        val date = dateSeconds ?: return Result.Rejected("MISSING_DATE")
        val mediaType = contentType ?: return Result.Rejected("MISSING_CONTENT_TYPE")
        if (mediaType !in MULTIPART_TYPES) return Result.Rejected("UNSUPPORTED_CONTENT_TYPE")

        val offset = bodyOffset ?: return Result.Rejected("MISSING_BODY_OFFSET")
        if (offset >= pdu.size) return Result.Rejected("MISSING_MULTIPART_BODY")

        return Result.Accepted(
            Envelope(
                messageType = type,
                mmsVersion = version,
                dateSeconds = date,
                senderDisposition = if (senderSeen) senderDisposition else SenderDisposition.ABSENT,
                senderAddress = senderAddress,
                messageId = messageId,
                transactionId = transactionId,
                contentType = mediaType,
                bodyOffset = offset
            )
        )
    }

    private sealed interface FromValue {
        data class Address(val value: String) : FromValue
        data object InsertAddress : FromValue
    }

    private fun readFromValue(cursor: Cursor): FromValue? {
        val length = readValueLength(cursor) ?: return null
        if (length !in 1..MAX_FROM_VALUE_BYTES || length > cursor.remaining) return null
        val nested = Cursor(cursor.bytes, cursor.position, cursor.position + length)
        cursor.position += length

        return when (nested.read()) {
            FROM_ADDRESS_PRESENT_TOKEN -> {
                val raw = readEncodedStringBytes(nested, MAX_ADDRESS_CHARS) ?: return null
                if (nested.remaining != 0) return null
                val address = normalizeAddress(raw) ?: return null
                FromValue.Address(address)
            }
            FROM_INSERT_ADDRESS_TOKEN -> {
                if (nested.remaining != 0) return null
                FromValue.InsertAddress
            }
            else -> null
        }
    }

    private fun normalizeAddress(raw: ByteArray): String? {
        if (raw.isEmpty() || raw.size > MAX_ADDRESS_CHARS) return null
        if (raw.any { (it.toInt() and 0xff) !in 0x20..0x7e }) return null
        val decoded = raw.toString(Charsets.US_ASCII).trim()
        if (decoded.isEmpty()) return null
        val typeSeparator = decoded.indexOf('/')
        val withoutType = if (typeSeparator > 0) decoded.substring(0, typeSeparator) else decoded
        val normalized = withoutType.trim()
        if (normalized.isEmpty() || normalized.length > MAX_ADDRESS_CHARS) return null
        return normalized
    }

    private fun readEncodedStringBytes(cursor: Cursor, maxChars: Int): ByteArray? {
        val first = cursor.peek() ?: return null
        if (first == 0) {
            cursor.read()
            return ByteArray(0)
        }

        if (first < TEXT_MIN) {
            val length = readValueLength(cursor) ?: return null
            if (length <= 0 || length > MAX_ENCODED_STRING_BYTES || length > cursor.remaining) return null
            val nested = Cursor(cursor.bytes, cursor.position, cursor.position + length)
            cursor.position += length
            if (readIntegerValue(nested) == null) return null
            val value = readRawTextString(nested, maxChars) ?: return null
            if (nested.remaining != 0) return null
            return value
        }

        return readRawTextString(cursor, maxChars)
    }

    private fun skipEncodedStringValue(cursor: Cursor): Boolean {
        val first = cursor.peek() ?: return false
        if (first == 0) {
            cursor.read()
            return true
        }
        if (first < TEXT_MIN) {
            val length = readValueLength(cursor) ?: return false
            if (length <= 0 || length > MAX_ENCODED_STRING_BYTES || length > cursor.remaining) return false
            cursor.position += length
            return true
        }
        return readRawTextString(cursor, MAX_ENCODED_STRING_BYTES) != null
    }

    private fun skipMessageClass(cursor: Cursor): Boolean {
        val first = cursor.peek() ?: return false
        return if (first >= 0x80) {
            cursor.read()
            true
        } else {
            readRawTextString(cursor, MAX_TOKEN_TEXT_BYTES) != null
        }
    }

    private fun readContentType(cursor: Cursor): String? {
        val first = cursor.peek() ?: return null
        return when {
            first <= SHORT_LENGTH_MAX -> {
                val length = cursor.read() ?: return null
                parseContentTypeGeneralForm(cursor, length)
            }
            first == LENGTH_QUOTE -> {
                cursor.read()
                val length = readUintvar(cursor) ?: return null
                parseContentTypeGeneralForm(cursor, length)
            }
            else -> readMediaType(cursor)
        }
    }

    private fun parseContentTypeGeneralForm(cursor: Cursor, length: Int): String? {
        if (length <= 0 || length > MAX_CONTENT_TYPE_BYTES || length > cursor.remaining) return null
        val nested = Cursor(cursor.bytes, cursor.position, cursor.position + length)
        cursor.position += length
        return readMediaType(nested)
    }

    private fun readMediaType(cursor: Cursor): String? {
        val first = cursor.peek() ?: return null
        return if (first >= 0x80) {
            cursor.read()
            WELL_KNOWN_TYPES[first and 0x7f]
        } else {
            val bytes = readRawTextString(cursor, MAX_MIME_CHARS) ?: return null
            if (bytes.any { (it.toInt() and 0xff) !in 0x20..0x7e }) return null
            bytes.toString(Charsets.US_ASCII).lowercase().takeIf(::isSaneMime)
        }
    }

    private fun readAsciiTextString(cursor: Cursor, maxChars: Int): String? {
        val bytes = readRawTextString(cursor, maxChars) ?: return null
        if (bytes.isEmpty() || bytes.any { (it.toInt() and 0xff) !in 0x21..0x7e }) return null
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun readRawTextString(cursor: Cursor, maxChars: Int): ByteArray? {
        if (maxChars <= 0) return null
        if (cursor.peek() == QUOTE) cursor.read()
        val start = cursor.position
        var length = 0
        while (cursor.remaining > 0 && length <= maxChars) {
            val value = cursor.read() ?: return null
            if (value == 0) {
                if (length > maxChars) return null
                return cursor.bytes.copyOfRange(start, start + length)
            }
            if (value in 0x01..0x1f || value == 0x7f) return null
            length++
        }
        return null
    }

    private fun readIntegerValue(cursor: Cursor): Long? {
        val first = cursor.peek() ?: return null
        return if (first >= 0x80) {
            cursor.read()
            (first and 0x7f).toLong()
        } else {
            readLongInteger(cursor)
        }
    }

    private fun readShortInteger(cursor: Cursor): Int? {
        val value = cursor.read() ?: return null
        if (value < 0x80) return null
        return value and 0x7f
    }

    private fun readLongInteger(cursor: Cursor): Long? {
        val count = cursor.read() ?: return null
        if (count !in 1..8 || count > cursor.remaining) return null
        if (count > 1 && cursor.peek() == 0) return null // WSP requires minimal unsigned encoding.
        var result = 0L
        repeat(count) {
            val next = cursor.read() ?: return null
            if (result > (Long.MAX_VALUE ushr 8)) return null
            result = (result shl 8) or next.toLong()
        }
        return result
    }

    private fun readValueLength(cursor: Cursor): Int? {
        val first = cursor.read() ?: return null
        return when {
            first <= SHORT_LENGTH_MAX -> first
            first == LENGTH_QUOTE -> readUintvar(cursor)
            else -> null
        }
    }

    private fun readUintvar(cursor: Cursor): Int? {
        var value = 0L
        repeat(MAX_UINTVAR_BYTES) {
            val next = cursor.read() ?: return null
            value = (value shl 7) or (next and 0x7f).toLong()
            if (value > Int.MAX_VALUE) return null
            if (next and 0x80 == 0) return value.toInt()
        }
        return null
    }

    private fun isSaneMime(value: String): Boolean {
        if (value.length !in 3..MAX_MIME_CHARS) return false
        val slash = value.indexOf('/')
        if (slash <= 0 || slash == value.lastIndex || value.indexOf('/', slash + 1) != -1) return false
        return value.all { it.isLetterOrDigit() || it in "!#$&^_.+-/*" }
    }

    private class Cursor(
        val bytes: ByteArray,
        var position: Int,
        private val limit: Int
    ) {
        val remaining: Int get() = limit - position
        fun peek(): Int? = if (position < limit) bytes[position].toInt() and 0xff else null
        fun read(): Int? = if (position < limit) bytes[position++].toInt() and 0xff else null
    }

    private const val MAX_PDU_BYTES = 17 * 1024 * 1024
    private const val MAX_ADDRESS_CHARS = 256
    private const val MAX_ID_CHARS = 256
    private const val MAX_FROM_VALUE_BYTES = 1024
    private const val MAX_ENCODED_STRING_BYTES = 4096
    private const val MAX_TOKEN_TEXT_BYTES = 256
    private const val MAX_CONTENT_TYPE_BYTES = 1024
    private const val MAX_MIME_CHARS = 128
    private const val MAX_UINTVAR_BYTES = 5

    private const val TEXT_MIN = 0x20
    private const val QUOTE = 0x7f
    private const val SHORT_LENGTH_MAX = 30
    private const val LENGTH_QUOTE = 31

    private const val HEADER_BCC = 0x81
    private const val HEADER_CC = 0x82
    private const val HEADER_CONTENT_TYPE = 0x84
    private const val HEADER_DATE = 0x85
    private const val HEADER_DELIVERY_REPORT = 0x86
    private const val HEADER_FROM = 0x89
    private const val HEADER_MESSAGE_CLASS = 0x8a
    private const val HEADER_MESSAGE_ID = 0x8b
    private const val HEADER_MESSAGE_TYPE = 0x8c
    private const val HEADER_MMS_VERSION = 0x8d
    private const val HEADER_PRIORITY = 0x8f
    private const val HEADER_READ_REPLY = 0x90
    private const val HEADER_REPORT_ALLOWED = 0x91
    private const val HEADER_RESPONSE_TEXT = 0x93
    private const val HEADER_SENDER_VISIBILITY = 0x94
    private const val HEADER_SUBJECT = 0x96
    private const val HEADER_TO = 0x97
    private const val HEADER_TRANSACTION_ID = 0x98
    private const val HEADER_RETRIEVE_STATUS = 0x99
    private const val HEADER_RETRIEVE_TEXT = 0x9a
    private const val HEADER_STORE_STATUS_TEXT = 0xa6
    private const val HEADER_STATUS_TEXT = 0xb6
    private const val HEADER_CONTENT_CLASS = 0xba
    private const val HEADER_DRM_CONTENT = 0xbb
    private const val HEADER_ADAPTATION_ALLOWED = 0xbc

    private const val FROM_ADDRESS_PRESENT_TOKEN = 0x80
    private const val FROM_INSERT_ADDRESS_TOKEN = 0x81
    private const val MESSAGE_TYPE_RETRIEVE_CONF = 0x84

    private val SUPPORTED_MMS_VERSIONS = setOf(0x10, 0x11, 0x12, 0x13)
    private val MULTIPART_TYPES = setOf(
        "application/vnd.wap.multipart.mixed",
        "application/vnd.wap.multipart.alternative",
        "application/vnd.wap.multipart.related"
    )
    private val WELL_KNOWN_TYPES = mapOf(
        0x23 to "application/vnd.wap.multipart.mixed",
        0x26 to "application/vnd.wap.multipart.alternative",
        0x33 to "application/vnd.wap.multipart.related"
    )
}
