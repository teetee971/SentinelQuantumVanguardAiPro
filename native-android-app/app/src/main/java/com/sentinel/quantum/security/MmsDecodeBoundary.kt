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
            safe += SafePart(mime, part.fileName?.trim()?.takeIf { it.isNotEmpty() }, part.payload.copyOf())
        }
        return Result.Accepted(safe)
    }

    private const val MAX_PARTS = 32
    private const val MAX_TOTAL_DECODED_BYTES = 16L * 1024L * 1024L
}
