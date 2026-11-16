package com.sentinel.quantum.security

/**
 * Recovers the bounded top-level multipart/related Content-Type parameters that
 * [MmsRetrieveEnvelopeParser] intentionally does not interpret.
 *
 * The envelope parser already proves [MmsRetrieveEnvelopeParser.Envelope.bodyOffset]. We therefore
 * accept only a structurally unique X-Mms-Content-Type field whose encoded value ends exactly at
 * that offset. Ambiguous candidates, unsupported parameters and malformed values fail closed.
 */
internal object MmsRelatedPresentationInspector {
    data class Metadata(
        val startContentId: String?,
        val rootContentType: String
    )

    sealed interface Result {
        data class Ready(val metadata: Metadata) : Result
        data class Rejected(val reason: String) : Result
    }

    fun inspect(
        pdu: ByteArray,
        envelope: MmsRetrieveEnvelopeParser.Envelope
    ): Result {
        if (envelope.contentType != RELATED_MIME) return Result.Rejected("NOT_MULTIPART_RELATED")
        val end = envelope.bodyOffset
        if (end !in 2..pdu.size) return Result.Rejected("INVALID_CONTENT_TYPE_BOUNDARY")

        val candidates = ArrayList<Parsed>(2)
        for (offset in 0 until end - 1) {
            if (u(pdu[offset]) != HEADER_CONTENT_TYPE) continue
            when (val parsed = parseCandidate(pdu, offset + 1, end)) {
                is Candidate.Valid -> {
                    if (parsed.value.mediaType == envelope.contentType) candidates += parsed.value
                }
                Candidate.NotCandidate -> Unit
                is Candidate.Invalid -> {
                    // Only a structurally aligned related Content-Type candidate is authoritative.
                    if (parsed.mediaType == envelope.contentType) {
                        return Result.Rejected(parsed.reason)
                    }
                }
            }
        }

        if (candidates.size != 1) {
            return Result.Rejected(
                if (candidates.isEmpty()) "RELATED_CONTENT_TYPE_PARAMETERS_NOT_RECOVERED"
                else "AMBIGUOUS_RELATED_CONTENT_TYPE_PARAMETERS"
            )
        }
        val parsed = candidates.single()
        val rootType = parsed.rootContentType
            ?: return Result.Rejected("RELATED_ROOT_TYPE_REQUIRED")
        if (rootType != SMIL_MIME) return Result.Rejected("RELATED_ROOT_TYPE_UNSUPPORTED")
        return Result.Ready(Metadata(parsed.startContentId, rootType))
    }

    private data class Parsed(
        val mediaType: String,
        val startContentId: String?,
        val rootContentType: String?
    )

    private sealed interface Candidate {
        data class Valid(val value: Parsed) : Candidate
        data class Invalid(val mediaType: String?, val reason: String) : Candidate
        data object NotCandidate : Candidate
    }

    private fun parseCandidate(bytes: ByteArray, start: Int, end: Int): Candidate {
        val cursor = Cursor(bytes, start, end)
        val valueLength = readValueLength(cursor) ?: return Candidate.NotCandidate
        if (valueLength <= 0 || valueLength > MAX_CONTENT_TYPE_BYTES) return Candidate.NotCandidate
        if (cursor.position + valueLength != end) return Candidate.NotCandidate

        val nested = Cursor(bytes, cursor.position, end)
        val mediaType = readMediaType(nested) ?: return Candidate.NotCandidate
        if (mediaType != RELATED_MIME) return Candidate.Valid(Parsed(mediaType, null, null))

        var startContentId: String? = null
        var rootContentType: String? = null
        while (nested.remaining > 0) {
            when (nested.read() ?: return Candidate.Invalid(mediaType, "TRUNCATED_RELATED_PARAMETER")) {
                PARAM_TYPE, PARAM_CT_MR_TYPE -> {
                    if (rootContentType != null) {
                        return Candidate.Invalid(mediaType, "DUPLICATE_RELATED_TYPE_PARAMETER")
                    }
                    rootContentType = readConstrainedMedia(nested)
                        ?: return Candidate.Invalid(mediaType, "INVALID_RELATED_TYPE_PARAMETER")
                }
                PARAM_DEP_START, PARAM_START -> {
                    if (startContentId != null) {
                        return Candidate.Invalid(mediaType, "DUPLICATE_RELATED_START_PARAMETER")
                    }
                    startContentId = readTextString(nested)
                        ?.takeIf(::isSaneReference)
                        ?: return Candidate.Invalid(mediaType, "INVALID_RELATED_START_PARAMETER")
                }
                else -> return Candidate.Invalid(mediaType, "UNSUPPORTED_RELATED_PARAMETER")
            }
        }
        return Candidate.Valid(Parsed(mediaType, startContentId, rootContentType))
    }

    private fun readConstrainedMedia(cursor: Cursor): String? {
        val first = cursor.peek() ?: return null
        return if (first >= 0x80) {
            cursor.read()
            WELL_KNOWN_TYPES[first and 0x7f]
        } else {
            readTextString(cursor)?.lowercase()?.takeIf(::isSaneMime)
        }
    }

    private fun readMediaType(cursor: Cursor): String? = readConstrainedMedia(cursor)

    private fun readTextString(cursor: Cursor): String? {
        if (cursor.peek() == QUOTE) cursor.read()
        val start = cursor.position
        var length = 0
        while (cursor.remaining > 0 && length <= MAX_TEXT_CHARS) {
            val value = cursor.read() ?: return null
            if (value == 0) {
                if (length == 0 || length > MAX_TEXT_CHARS) return null
                return cursor.bytes.copyOfRange(start, start + length).toString(Charsets.US_ASCII)
            }
            if (value !in 0x20..0x7e) return null
            length++
        }
        return null
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

    private fun isSaneReference(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_REFERENCE_CHARS &&
            value.all { it.code in 0x20..0x7e } &&
            value.none { it == '\r' || it == '\n' || it == '\u0000' }

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

    private const val HEADER_CONTENT_TYPE = 0x84
    private const val PARAM_TYPE = 0x83
    private const val PARAM_CT_MR_TYPE = 0x89
    private const val PARAM_DEP_START = 0x8a
    private const val PARAM_START = 0x99
    private const val QUOTE = 0x7f
    private const val SHORT_LENGTH_MAX = 30
    private const val LENGTH_QUOTE = 31
    private const val MAX_UINTVAR_BYTES = 5
    private const val MAX_CONTENT_TYPE_BYTES = 1024
    private const val MAX_TEXT_CHARS = 512
    private const val MAX_REFERENCE_CHARS = 512
    private const val MAX_MIME_CHARS = 128
    private const val RELATED_MIME = "application/vnd.wap.multipart.related"
    private const val SMIL_MIME = "application/smil"

    private val WELL_KNOWN_TYPES = mapOf(
        0x03 to "text/plain",
        0x1d to "image/gif",
        0x1e to "image/jpeg",
        0x20 to "image/png",
        0x23 to "application/vnd.wap.multipart.mixed",
        0x26 to "application/vnd.wap.multipart.alternative",
        0x33 to RELATED_MIME
    )
}
