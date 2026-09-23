package com.sentinel.quantum.security

/**
 * Small, dependency-free WSP/MMS multipart decoder used only for bounded safe previews.
 *
 * It deliberately supports the subset needed by the existing preview boundary:
 * text/plain and common image parts inside WAP multipart mixed/related/alternative messages.
 * Unknown encodings, ambiguous bodies and malformed lengths fail closed.
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
            cursor.position = headerEnd

            if (dataLength > cursor.remaining) return null
            val dataStart = cursor.position
            val dataEnd = dataStart + dataLength
            val payload = cursor.bytes.copyOfRange(dataStart, dataEnd)
            cursor.position = dataEnd

            // SMIL is layout metadata, never rendered by Sentinel's preview path.
            if (mime == "application/smil") return@repeat
            parts += MmsDecodeBoundary.DecodedPart(
                mimeType = mime,
                fileName = null,
                payload = payload
            )
        }
        return parts
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
        cursor.position = end // Skip parameters inside the declared Content-Type value.
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
    private const val MAX_PDU_BYTES = 17 * 1024 * 1024
    private const val MAX_PARTS = 32
    private const val MAX_PART_BYTES = 8 * 1024 * 1024
    private const val MAX_PART_HEADER_BYTES = 4096
    private const val MAX_CONTENT_TYPE_BYTES = 512
    private const val MAX_TEXT_BYTES = 256
    private const val MAX_MIME_CHARS = 128
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
