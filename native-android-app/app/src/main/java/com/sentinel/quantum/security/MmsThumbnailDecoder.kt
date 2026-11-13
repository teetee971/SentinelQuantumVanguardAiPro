package com.sentinel.quantum.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream

internal object MmsThumbnailDecoder {

    fun decode(
        bytes: ByteArray,
        mimeType: String,
        targetDimension: Int = 256
    ): Bitmap? {
        if (bytes.isEmpty()) return null
        if (bytes.size > MmsThumbnailPolicy.MAX_COMPRESSED_BYTES) {
            return null
        }

        val mime = mimeType.trim().lowercase()

        if (!matchesSignature(bytes, mime)) return null

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        try {
            BitmapFactory.decodeStream(
                ByteArrayInputStream(bytes),
                null,
                bounds
            )
        } catch (_: RuntimeException) {
            return null
        } catch (_: OutOfMemoryError) {
            return null
        }

        val sample = MmsThumbnailPolicy.sampleSize(
            bounds.outWidth,
            bounds.outHeight,
            targetDimension
        ) ?: return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
            inScaled = false
        }

        return try {
            val bitmap = BitmapFactory.decodeStream(
                ByteArrayInputStream(bytes),
                null,
                options
            ) ?: return null

            val valid =
                bitmap.width > 0 &&
                bitmap.height > 0 &&
                bitmap.width.toLong() * bitmap.height.toLong() <=
                    MmsThumbnailPolicy.MAX_THUMBNAIL_PIXELS

            if (!valid) {
                bitmap.recycle()
                null
            } else {
                bitmap
            }
        } catch (_: RuntimeException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    internal fun matchesSignature(
        bytes: ByteArray,
        mimeType: String
    ): Boolean {
        fun prefix(vararg values: Int): Boolean =
            bytes.size >= values.size &&
                values.indices.all {
                    (bytes[it].toInt() and 255) == values[it]
                }

        return when (mimeType) {
            "image/jpeg" -> prefix(0xFF, 0xD8, 0xFF)

            "image/png" -> prefix(
                0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
            )

            "image/gif" ->
                prefix(0x47, 0x49, 0x46, 0x38, 0x37, 0x61) ||
                prefix(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)

            "image/webp" ->
                bytes.size >= 12 &&
                prefix(0x52, 0x49, 0x46, 0x46) &&
                bytes.copyOfRange(8, 12)
                    .contentEquals(
                        byteArrayOf(0x57, 0x45, 0x42, 0x50)
                    )

            else -> false
        }
    }
}
