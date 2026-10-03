package com.sentinel.quantum.security

/**
 * Small, dependency-free WSP/MMS multipart decoder used only for bounded safe previews/provider
 * projection.
 *
 * It deliberately supports the subset needed by the existing boundary: text/plain, SMIL metadata
 * and common image parts inside WAP multipart mixed/related/alternative messages. Content-ID and
 * Content-Location are preserved when encoded as standard part headers; bounded
 * Content-Disposition is skipped without interpretation so later references remain reachable.
 * Unknown encodings, ambiguous bodies and malformed lengths fail closed.
 */
object SentinelMmsPduDecoder : MmsPduDecoder {
    override fun decode(pdu: ByteArray): MmsPduDecoder.DecodeResult {
        if (pdu.isEmpty() || pdu.size > MAX_PDU_BYTES) {
            return MmsPduDecoder.DecodeResult.Rejected("INVALID_PDU_SIZE")
        }

        // Legacy/general preview path. It remains intentionally conservative because callers that
        // do not possess a parsed MMS envelope cannot safely assume which 0x84 byte is top-level
        // Content-Type. Incoming M-Retrieve.conf projection must use decodeRetrieveBody() below.
        val candidates = ArrayList<List<MmsDecodeBoundary.DecodedPart>>(2)
        for (offset in 0 until pdu.lastIndex) {
            if (u(pdu[offset]) != CONTENT_TYPE_HEADER) continue
            val cursor = Cursor(pdu, offset + 1, pdu.size)
            val topLevelType = parseContentType(cursor) ?: continue
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

    /**
     * Authoritative M-Retrieve.conf body decoder.
     *
     * [MmsRetrieveEnvelopeParser] has already consumed the top-level Content-Type and returns the
     * exact first body byte. Starting there prevents a 0x84 byte inside text/image payload from
     * being misinterpreted as a second candidate top-level header.
     */
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
            val mime = parseContentType(headerCursor) ?: return null
            val references = parsePartReferences(headerCursor) ?: return null
            cursor.position = headerEnd

            if (dataLength > cursor.remaining) return null
            val dataStart = cursor.position
            val dataEnd = dataStart + dataLength
            val payload = cursor.bytes.copyOfRange(dataStart, dataEnd)
            cursor.position = dataEnd

            parts += MmsDecodeBoundary.DecodedPart(
                mimeType = mime,
                fileName = null,
                payload = payload,
                contentId = references.contentId,
                contentLocation = references.contentLocation
            )
        }
        return parts
    }

    /**
     * Parses only reference metadata required to preserve multipart/related semantics. Standard
     * Content-Disposition is skipped by its declared bounded value length. Unknown headers stop
     * interpretation rather than guessing their value shape, preventing unsafe re-synchronization.
     */
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
                    // The full part-header block is already length-bounded. Do not guess how to
                    // skip an unsupported WSP value because doing so could re-synchronize on bytes
                    // inside that value and manufacture reference metadata.
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

    private fun parseContentType(cursor: Cursor): String? {
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
            else -> parseMediaType(cursor)
        }
    }

    private fun parseGeneralForm(cursor: Cursor, declaredLength: Int): String? {
        if (declaredLength <= 0 || declaredLength > cursor.remaining || declaredLength > MAX_CONTENT_TYPE_BYTES) {
            return null
        }
        val end = cursor.position + declaredLength
        val nested = Cursor(cursor.bytes, cursor.position, end)
        val mime = parseMediaType(nested) ?: return null
        cursor.position = end // Skip bounded Content-Type parameters.
        return mime
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
    private const val MAX_PDU_BYTES = 17 * 1024 * 1024
    private const val MAX_PARTS = 32
    private const val MAX_PART_BYTES = 8 * 1024 * 1024
    private const val MAX_PART_HEADER_BYTES = 4096
    private const val MAX_CONTENT_TYPE_BYTES = 512
    private const val MAX_TEXT_BYTES = 512
    private const val MAX_MIME_CHARS = 128
    private const val MAX_REFERENCE_CHARS = 512
    private const val MAX_UINTVAR_BYTES = 5

    private val MULTIPART_TYPES = setOf(
        "application/vnd.wap.multipart.mixed",
        "application/vnd.wap.multipart.alternative",
        "application/vnd.wap.multipart.related"
    )

    // WSP well-known media indexes used by the safe preview path.
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
