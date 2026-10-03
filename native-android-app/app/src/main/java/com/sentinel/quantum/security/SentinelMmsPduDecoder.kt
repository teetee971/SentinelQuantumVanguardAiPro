package com.sentinel.quantum.security

/**
 * Small, dependency-free WSP/MMS multipart decoder used only for bounded safe previews/provider
 * projection.
 *
 * It deliberately supports the subset needed by the existing boundary: text/plain, SMIL metadata
 * and common image parts inside WAP multipart mixed/related/alternative messages. Content-ID,
 * Content-Location and supported charset metadata are preserved. Bounded Content-Disposition is
 * skipped without interpretation so later references remain reachable. Unknown encodings,
 * ambiguous bodies and malformed lengths fail closed.
 */
object SentinelMmsPduDecoder : MmsPduDecoder {
    override fun decode(pdu: ByteArray): MmsPduDecoder.DecodeResult {
        if (pdu.isEmpty() || pdu.size > MAX_PDU_BYTES) {
            return MmsPduDecoder.DecodeResult.Rejected("INVALID_PDU_SIZE")
        }

        val candidates = ArrayList<List<MmsDecodeBoundary.DecodedPart>>(2)
        for (offset in 0 until pdu.lastIndex) {
            if (u(pdu[offset]) != CONTENT_TYPE_HEADER) continue
            val cursor = Cursor(pdu, offset + 1, pdu.size)
            val topLevelType = parseContentType(cursor)?.mimeType ?: continue
            if (topLevelType !in MULTIPART_TYPES) continue
            val parts = parseMultipart(cursor) ?: continue
            if (cursor.position != pdu.size || parts.isEmpty()) continue
            candidates += parts
            if (candidates.size > 1) {
                return MmsPduDecoder.DecodeResult.Rejected("AMBIGUOUS_MULTIPART_BODY")
            }
        }

        val parts = candidates.singleOrNull()
            ?: return MmsPduDecoder.DecodeResult.Rejected("SUPPORTED_MULTIPART_BODY_NOT_FOUND")
        return MmsPduDecoder.DecodeResult.Decoded(parts)
    }

    internal fun decodeRetrieveBody(
        pdu: ByteArray,
        envelope: MmsRetrieveEnvelopeParser.Envelope
    ): MmsPduDecoder.DecodeResult {
        if (pdu.isEmpty() || pdu.size > MAX_PDU_BYTES) {
            return MmsPduDecoder.DecodeResult.Rejected("INVALID_PDU_SIZE")
        }
        if (envelope.messageType != MESSAGE_TYPE_RETRIEVE_CONF) {
            return MmsPduDecoder.DecodeResult.Rejected("NOT_RETRIEVE_CONF")
        }
        if (envelope.contentType !in MULTIPART_TYPES) {
            return MmsPduDecoder.DecodeResult.Rejected("UNSUPPORTED_MULTIPART_TYPE")
        }
        val offset = envelope.bodyOffset
        if (offset !in 1 until pdu.size) {
            return MmsPduDecoder.DecodeResult.Rejected("INVALID_BODY_OFFSET")
        }
        val cursor = Cursor(pdu, offset, pdu.size)
        val parts = parseMultipart(cursor)
            ?: return MmsPduDecoder.DecodeResult.Rejected("INVALID_MULTIPART_BODY")
        if (cursor.position != pdu.size || parts.isEmpty()) {
            return MmsPduDecoder.DecodeResult.Rejected("TRAILING_OR_EMPTY_MULTIPART_BODY")
        }
        return MmsPduDecoder.DecodeResult.Decoded(parts)
    }

    private data class PartReferences(
        val contentId: String?,
        val contentLocation: String?
    )

    private data class ParsedContentType(
        val mimeType: String,
        val charsetMibEnum: Int?
    )

    private fun parseMultipart(cursor: Cursor): List<MmsDecodeBoundary.DecodedPart>? {
        val count = readUintvar(cursor) ?: return null
        if (count !in 1..MAX_PARTS) return null

        val parts = ArrayList<MmsDecodeBoundary.DecodedPart>(count)
        repeat(count) {
            val headerLength = readUintvar(cursor) ?: return null
            val dataLength = readUintvar(cursor) ?: return null
            if (headerLength !in 1..MAX_PART_HEADER_BYTES) return null
            if (dataLength !in 0..MAX_PART_BYTES) return null
            if (headerLength > cursor.remaining) return null

            val headerEnd = cursor.position + headerLength
            val headerCursor = Cursor(cursor.bytes, cursor.position, headerEnd)
            val contentType = parseContentType(headerCursor) ?: return null
            val references = parsePartReferences(headerCursor) ?: return null
            cursor.position = headerEnd

            if (dataLength > cursor.remaining) return null
            val dataStart = cursor.position
            val dataEnd = dataStart + dataLength
            val payload = cursor.bytes.copyOfRange(dataStart, dataEnd)
            cursor.position = dataEnd

            parts += MmsDecodeBoundary.DecodedPart(
                mimeType = contentType.mimeType,
                fileName = null,
                payload = payload,
                contentId = references.contentId,
                contentLocation = references.contentLocation,
                charsetMibEnum = contentType.charsetMibEnum
            )
        }
        return parts
    }

    private fun parsePartReferences(cursor: Cursor): PartReferences? {
        var contentId: String? = null
        var contentLocation: String? = null
        while (cursor.remaining > 0) {
            when (cursor.read() ?: return null) {
                PART_CONTENT_LOCATION -> {
                    if (contentLocation != null) return null
                    contentLocation = readTextString(cursor)?.takeIf(::isSaneReference) ?: return null
                }
                PART_CONTENT_ID -> {
                    if (contentId != null) return null
                    contentId = readQuotedString(cursor)?.takeIf(::isSaneReference) ?: return null
                }
                PART_DEP_CONTENT_DISPOSITION,
                PART_CONTENT_DISPOSITION -> {
                    if (!skipLengthDelimitedValue(cursor)) return null
                }
                else -> {
                    cursor.position += cursor.remaining
                }
            }
        }
        return PartReferences(contentId, contentLocation)
    }

    private fun skipLengthDelimitedValue(cursor: Cursor): Boolean {
        val first = cursor.read() ?: return false
        val length = when {
            first in 0..30 -> first
            first == 31 -> readUintvar(cursor) ?: return false
            else -> return false
        }
        if (length <= 0 || length > MAX_PART_HEADER_BYTES || length > cursor.remaining) return false
        cursor.position += length
        return true
    }

    private fun parseContentType(cursor: Cursor): ParsedContentType? {
        val first = cursor.peek() ?: return null
        return when {
            first in 0..30 -> {
                cursor.read()
                parseGeneralForm(cursor, first)
            }
            first == 31 -> {
                cursor.read()
                val length = readUintvar(cursor) ?: return null
                parseGeneralForm(cursor, length)
            }
            else -> parseMediaType(cursor)?.let { ParsedContentType(it, null) }
        }
    }

    private fun parseGeneralForm(cursor: Cursor, declaredLength: Int): ParsedContentType? {
        if (declaredLength <= 0 || declaredLength > cursor.remaining || declaredLength > MAX_CONTENT_TYPE_BYTES) {
            return null
        }
        val end = cursor.position + declaredLength
        val nested = Cursor(cursor.bytes, cursor.position, end)
        val mime = parseMediaType(nested) ?: return null
        var charsetMibEnum: Int? = null
        var stopParsingParameters = false
        while (nested.remaining > 0 && !stopParsingParameters) {
            when (nested.read() ?: return null) {
                PARAM_CHARSET -> {
                    if (charsetMibEnum != null) return null
                    charsetMibEnum = readCharset(nested) ?: return null
                }
                PARAM_DEP_NAME,
                PARAM_DEP_FILENAME,
                PARAM_NAME,
                PARAM_FILENAME -> {
                    if (readTextString(nested) == null) return null
                }
                else -> {
                    // Do not guess the shape of an unsupported parameter. Remaining bytes are
                    // still bounded by the declared Content-Type value; charset then stays absent
                    // and text validation falls back to strict UTF-8 rather than inventing metadata.
                    nested.position += nested.remaining
                    stopParsingParameters = true
                }
            }
        }
        cursor.position = end
        return ParsedContentType(mime, charsetMibEnum)
    }

    private fun readCharset(cursor: Cursor): Int? {
        val first = cursor.peek() ?: return null
        return when {
            first >= 0x80 -> {
                cursor.read()
                first and 0x7f
            }
            first in 1..MAX_INTEGER_BYTES -> {
                val length = cursor.read() ?: return null
                if (length > cursor.remaining) return null
                var value = 0L
                repeat(length) {
                    value = (value shl 8) or (cursor.read() ?: return null).toLong()
                    if (value > Int.MAX_VALUE) return null
                }
                value.toInt()
            }
            first in 0x20..0x7e -> {
                val name = readTextString(cursor)?.lowercase() ?: return null
                when (name) {
                    "us-ascii" -> MmsTextCharset.US_ASCII
                    "iso-8859-1" -> MmsTextCharset.ISO_8859_1
                    "utf-8" -> MmsTextCharset.UTF_8
                    "iso-10646-ucs-2" -> MmsTextCharset.UCS2
                    "utf-16" -> MmsTextCharset.UTF_16
                    else -> return null
                }
            }
            else -> null
        }
    }

    private fun parseMediaType(cursor: Cursor): String? {
        val first = cursor.peek() ?: return null
        return if (first >= 0x80) {
            cursor.read()
            WELL_KNOWN_TYPES[first and 0x7f]
        } else {
            readTextString(cursor)?.lowercase()?.takeIf(::isSaneMime)
        }
    }

    private fun readTextString(cursor: Cursor): String? {
        if (cursor.peek() == 0x7f) cursor.read()
        return readVisibleString(cursor)
    }

    private fun readQuotedString(cursor: Cursor): String? {
        if (cursor.peek() == 0x22) cursor.read()
        return readVisibleString(cursor)
    }

    private fun readVisibleString(cursor: Cursor): String? {
        val start = cursor.position
        var length = 0
        while (cursor.remaining > 0 && length <= MAX_TEXT_BYTES) {
            val value = cursor.read() ?: return null
            if (value == 0) {
                if (length == 0) return null
                val bytes = cursor.bytes.copyOfRange(start, start + length)
                return bytes.toString(Charsets.US_ASCII)
            }
            if (value !in 0x20..0x7e) return null
            length++
        }
        return null
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

    private fun isSaneReference(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= MAX_REFERENCE_CHARS &&
            value.all { it.code in 0x20..0x7e }

    private class Cursor(
        val bytes: ByteArray,
        var position: Int,
        private val limit: Int
    ) {
        val remaining: Int get() = limit - position
        fun peek(): Int? = if (position < limit) bytes[position].toInt() and 0xff else null
        fun read(): Int? = if (position < limit) bytes[position++].toInt() and 0xff else null
    }

    private fun u(value: Byte): Int = value.toInt() and 0xff

    private const val CONTENT_TYPE_HEADER = 0x84
    private const val MESSAGE_TYPE_RETRIEVE_CONF = 0x84
    private const val PART_CONTENT_LOCATION = 0x8e
    private const val PART_DEP_CONTENT_DISPOSITION = 0xae
    private const val PART_CONTENT_ID = 0xc0
    private const val PART_CONTENT_DISPOSITION = 0xc5
    private const val PARAM_CHARSET = 0x81
    private const val PARAM_DEP_NAME = 0x85
    private const val PARAM_DEP_FILENAME = 0x86
    private const val PARAM_NAME = 0x97
    private const val PARAM_FILENAME = 0x98
    private const val MAX_PDU_BYTES = 17 * 1024 * 1024
    private const val MAX_PARTS = 32
    private const val MAX_PART_BYTES = 8 * 1024 * 1024
    private const val MAX_PART_HEADER_BYTES = 4096
    private const val MAX_CONTENT_TYPE_BYTES = 512
    private const val MAX_TEXT_BYTES = 512
    private const val MAX_MIME_CHARS = 128
    private const val MAX_REFERENCE_CHARS = 512
    private const val MAX_UINTVAR_BYTES = 5
    private const val MAX_INTEGER_BYTES = 4

    private val MULTIPART_TYPES = setOf(
        "application/vnd.wap.multipart.mixed",
        "application/vnd.wap.multipart.alternative",
        "application/vnd.wap.multipart.related"
    )

    private val WELL_KNOWN_TYPES = mapOf(
        0x03 to "text/plain",
        0x1d to "image/gif",
        0x1e to "image/jpeg",
        0x20 to "image/png",
        0x23 to "application/vnd.wap.multipart.mixed",
        0x26 to "application/vnd.wap.multipart.alternative",
        0x33 to "application/vnd.wap.multipart.related"
    )
}
