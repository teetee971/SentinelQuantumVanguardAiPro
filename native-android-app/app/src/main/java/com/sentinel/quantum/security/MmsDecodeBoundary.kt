package com.sentinel.quantum.security

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Pure, fail-closed boundary between untrusted MMS decoder output and any future renderer/provider.
 *
 * Payload bytes are never exposed unless metadata passes [MmsPartSafetyPolicy] and all aggregate
 * bounds remain valid. Content-ID/Content-Location are preserved as inert provider metadata so a
 * validated multipart/related SMIL presentation can keep its original references without Sentinel
 * interpreting or executing the SMIL document.
 */
object MmsDecodeBoundary {
    data class DecodedPart(
        val mimeType: String?,
        val fileName: String?,
        val payload: ByteArray,
        val contentId: String? = null,
        val contentLocation: String? = null
    )

    data class SafePart(
        val mimeType: String,
        val fileName: String?,
        val payload: ByteArray,
        val contentId: String? = null,
        val contentLocation: String? = null
    )

    sealed class Result {
        data class Accepted(val parts: List<SafePart>) : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun validate(parts: List<DecodedPart>): Result {
        if (parts.isEmpty() || parts.size > MAX_PARTS) return Result.Rejected("INVALID_PART_COUNT")
        var totalBytes = 0L
        val safe = ArrayList<SafePart>(parts.size)
        for (part in parts) {
            val size = part.payload.size.toLong()
            totalBytes += size
            if (totalBytes > MAX_TOTAL_DECODED_BYTES) return Result.Rejected("DECODED_MESSAGE_TOO_LARGE")
            val policy = MmsPartSafetyPolicy.evaluate(
                MmsPartSafetyPolicy.PartMetadata(part.mimeType, part.fileName, size)
            )
            if (policy.decision == MmsPartSafetyPolicy.Decision.QUARANTINE) {
                return Result.Rejected(policy.reason)
            }
            val mime = part.mimeType?.trim()?.lowercase().orEmpty()
            if (mime.isEmpty()) return Result.Rejected("MISSING_MIME")
            if (!contentMatchesMime(mime, part.payload)) return Result.Rejected("CONTENT_SIGNATURE_MISMATCH")
            val contentId = normalizeReference(part.contentId)
                ?: if (part.contentId == null) null else return Result.Rejected("UNSAFE_CONTENT_ID")
            val contentLocation = normalizeReference(part.contentLocation)
                ?: if (part.contentLocation == null) null else return Result.Rejected("UNSAFE_CONTENT_LOCATION")
            safe += SafePart(
                mimeType = mime,
                fileName = part.fileName?.trim()?.takeIf { it.isNotEmpty() },
                payload = part.payload.copyOf(),
                contentId = contentId,
                contentLocation = contentLocation
            )
        }
        return Result.Accepted(safe)
    }

    private fun contentMatchesMime(mime: String, bytes: ByteArray): Boolean = when (mime) {
        "text/plain" -> bytes.none { it == 0.toByte() }
        "application/smil" -> isBoundedSmil(bytes)
        "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        "image/png" -> bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        "image/gif" -> bytes.size >= 6 && (String(bytes.copyOfRange(0, 6), Charsets.US_ASCII) == "GIF87a" || String(bytes.copyOfRange(0, 6), Charsets.US_ASCII) == "GIF89a")
        "image/webp" -> bytes.size >= 12 &&
            String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" &&
            String(bytes.copyOfRange(8, 12), Charsets.US_ASCII) == "WEBP"
        else -> false
    }

    private fun isBoundedSmil(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size > MAX_SMIL_BYTES || bytes.any { it == 0.toByte() }) return false
        val text = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull() ?: return false
        return text.indexOf("<smil", ignoreCase = true) >= 0
    }

    private fun normalizeReference(value: String?): String? {
        if (value == null) return null
        val normalized = value.trim()
        if (normalized.isEmpty() || normalized.length > MAX_REFERENCE_CHARS) return null
        if (!normalized.all { it.code in 0x20..0x7e }) return null
        if (normalized.any { it == '\u0000' || it == '\r' || it == '\n' }) return null
        return normalized
    }

    private const val MAX_PARTS = 32
    private const val MAX_TOTAL_DECODED_BYTES = 16L * 1024L * 1024L
    private const val MAX_SMIL_BYTES = 256 * 1024
    private const val MAX_REFERENCE_CHARS = 512
}
