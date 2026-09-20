package com.sentinel.quantum.security

/**
 * Fail-closed policy for MMS parts before any future renderer or exporter is allowed to see them.
 * Metadata comes from a decoder; this policy never executes or renders payload bytes.
 */
object MmsPartSafetyPolicy {
    enum class Decision { ALLOW_PREVIEW, QUARANTINE }

    data class PartMetadata(
        val mimeType: String?,
        val fileName: String?,
        val sizeBytes: Long
    )

    data class Result(val decision: Decision, val reason: String)

    fun evaluate(part: PartMetadata): Result {
        if (part.sizeBytes <= 0 || part.sizeBytes > MAX_PART_BYTES) {
            return Result(Decision.QUARANTINE, "INVALID_OR_OVERSIZED_PART")
        }
        val mime = part.mimeType?.trim()?.lowercase().orEmpty()
        if (mime !in SAFE_PREVIEW_MIME_TYPES) {
            return Result(Decision.QUARANTINE, "MIME_NOT_PREVIEWABLE")
        }
        val name = part.fileName?.trim().orEmpty()
        if (name.length > MAX_FILE_NAME_CHARS || name.contains('/') || name.contains('\\') || name.contains('\u0000')) {
            return Result(Decision.QUARANTINE, "UNSAFE_FILE_NAME")
        }
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty() && extension !in SAFE_EXTENSIONS) {
            return Result(Decision.QUARANTINE, "EXTENSION_MISMATCH_OR_UNSAFE")
        }
        if (extension.isNotEmpty() && extension !in extensionsForMime(mime)) {
            return Result(Decision.QUARANTINE, "MIME_EXTENSION_MISMATCH")
        }
        return Result(Decision.ALLOW_PREVIEW, "SAFE_PREVIEW_METADATA")
    }

    private fun extensionsForMime(mime: String): Set<String> = when (mime) {
        "text/plain" -> setOf("txt")
        "image/jpeg" -> setOf("jpg", "jpeg")
        "image/png" -> setOf("png")
        "image/webp" -> setOf("webp")
        "image/gif" -> setOf("gif")
        else -> emptySet()
    }

    private const val MAX_PART_BYTES = 8L * 1024L * 1024L
    private const val MAX_FILE_NAME_CHARS = 128
    private val SAFE_PREVIEW_MIME_TYPES = setOf("text/plain", "image/jpeg", "image/png", "image/webp", "image/gif")
    private val SAFE_EXTENSIONS = setOf("txt", "jpg", "jpeg", "png", "webp", "gif")
}
