package com.sentinel.quantum.security

/**
 * Pure, fail-closed boundary between untrusted MMS decoder output and any future renderer.
 *
 * A platform/library decoder may eventually populate [DecodedPart], but payload bytes are never
 * exposed here unless metadata passes MmsPartSafetyPolicy and all aggregate bounds remain valid.
 */
object MmsDecodeBoundary {
    data class DecodedPart(
        val mimeType: String?,
        val fileName: String?,
        val payload: ByteArray
    )

    data class SafePart(
        val mimeType: String,
        val fileName: String?,
        val payload: ByteArray
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
            if (policy.decision != MmsPartSafetyPolicy.Decision.ALLOW_PREVIEW) {
                return Result.Rejected(policy.reason)
            }
            val mime = part.mimeType?.trim()?.lowercase().orEmpty()
            if (mime.isEmpty()) return Result.Rejected("MISSING_MIME")
            if (!contentMatchesMime(mime, part.payload)) return Result.Rejected("CONTENT_SIGNATURE_MISMATCH")
            safe += SafePart(mime, part.fileName?.trim()?.takeIf { it.isNotEmpty() }, part.payload.copyOf())
        }
        return Result.Accepted(safe)
    }

    private fun contentMatchesMime(mime: String, bytes: ByteArray): Boolean = when (mime) {
        "text/plain" -> bytes.none { it == 0.toByte() }
        "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        "image/png" -> bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        "image/gif" -> bytes.size >= 6 && (String(bytes.copyOfRange(0, 6), Charsets.US_ASCII) == "GIF87a" || String(bytes.copyOfRange(0, 6), Charsets.US_ASCII) == "GIF89a")
        "image/webp" -> bytes.size >= 12 &&
            String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" &&
            String(bytes.copyOfRange(8, 12), Charsets.US_ASCII) == "WEBP"
        else -> false
    }

    private const val MAX_PARTS = 32
    private const val MAX_TOTAL_DECODED_BYTES = 16L * 1024L * 1024L
}
