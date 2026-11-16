package com.sentinel.quantum.security

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads user-selected MMS image attachments through ContentResolver without broad storage access.
 *
 * The loader is fail-closed: every file is bounded before materialization, the cumulative payload
 * is bounded, the declared MIME type is normalized to the small supported set, and the byte magic
 * must agree with that type before the attachment can enter the MMS transport.
 */
internal class MmsAttachmentLoader(context: Context) {
    data class LoadedAttachment(
        val mimeType: String,
        val payload: ByteArray
    ) {
        val sizeBytes: Long get() = payload.size.toLong()
    }

    sealed interface Result {
        data class Loaded(val attachments: List<LoadedAttachment>) : Result
        data class Rejected(val reason: String) : Result
    }

    private val resolver = context.applicationContext.contentResolver

    fun load(uris: List<Uri>): Result {
        if (uris.isEmpty()) return Result.Rejected("NO_ATTACHMENTS_SELECTED")
        if (uris.size > MmsSendEligibilityPolicy.MAX_ATTACHMENTS) {
            return Result.Rejected("TOO_MANY_ATTACHMENTS")
        }

        val loaded = ArrayList<LoadedAttachment>(uris.size)
        var totalBytes = 0L
        for (uri in uris) {
            if (uri.scheme != "content") return Result.Rejected("ATTACHMENT_URI_REJECTED")
            val mimeType = MmsAttachmentFormat.normalizeMimeType(
                runCatching { resolver.getType(uri) }.getOrNull()
            ) ?: return Result.Rejected("UNSUPPORTED_MIME_TYPE")

            val payload = runCatching {
                resolver.openInputStream(uri)?.use { stream ->
                    readBounded(stream, MmsSendEligibilityPolicy.MAX_ATTACHMENT_BYTES)
                }
            }.getOrNull() ?: return Result.Rejected("ATTACHMENT_READ_FAILED")

            if (payload.isEmpty()) return Result.Rejected("ATTACHMENT_EMPTY")
            if (!MmsAttachmentFormat.matchesPayload(mimeType, payload)) {
                return Result.Rejected("ATTACHMENT_FORMAT_MISMATCH")
            }
            if (Long.MAX_VALUE - totalBytes < payload.size.toLong()) {
                return Result.Rejected("ATTACHMENT_SIZE_OVERFLOW")
            }
            totalBytes += payload.size.toLong()
            if (totalBytes > MmsSendEligibilityPolicy.MAX_TOTAL_ATTACHMENT_BYTES) {
                return Result.Rejected("TOTAL_ATTACHMENT_SIZE_REJECTED")
            }
            loaded += LoadedAttachment(mimeType, payload)
        }
        return Result.Loaded(loaded)
    }

    internal companion object {
        fun readBounded(stream: InputStream, maxBytes: Long): ByteArray? {
            if (maxBytes <= 0L || maxBytes > Int.MAX_VALUE.toLong()) return null
            val out = ByteArrayOutputStream(minOf(maxBytes.toInt(), BUFFER_BYTES))
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read.toLong()
                if (total > maxBytes) return null
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }

        private const val BUFFER_BYTES = 8 * 1024
    }
}

internal object MmsAttachmentFormat {
    private val PNG = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    )
    private val GIF87A = "GIF87a".toByteArray(Charsets.US_ASCII)
    private val GIF89A = "GIF89a".toByteArray(Charsets.US_ASCII)
    private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
    private val WEBP = "WEBP".toByteArray(Charsets.US_ASCII)

    fun normalizeMimeType(raw: String?): String? = when (raw?.trim()?.lowercase()) {
        "image/jpeg", "image/jpg", "image/pjpeg" -> "image/jpeg"
        "image/png", "image/x-png" -> "image/png"
        "image/gif" -> "image/gif"
        "image/webp" -> "image/webp"
        else -> null
    }

    fun matchesPayload(mimeType: String, payload: ByteArray): Boolean = when (mimeType) {
        "image/jpeg" -> payload.size >= 3 &&
            payload[0] == 0xff.toByte() && payload[1] == 0xd8.toByte() && payload[2] == 0xff.toByte()
        "image/png" -> payload.startsWith(PNG)
        "image/gif" -> payload.startsWith(GIF87A) || payload.startsWith(GIF89A)
        "image/webp" -> payload.size >= 12 && payload.startsWith(RIFF) && payload.regionMatches(8, WEBP)
        else -> false
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.regionMatches(offset: Int, expected: ByteArray): Boolean =
        offset >= 0 && size - offset >= expected.size && expected.indices.all { this[offset + it] == expected[it] }
}
