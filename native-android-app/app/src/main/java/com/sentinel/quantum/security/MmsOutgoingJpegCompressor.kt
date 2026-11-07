package com.sentinel.quantum.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.ColorSpace
import android.os.Build
import java.nio.ByteBuffer
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStream

/** Bounded, side-effect-free JPEG validation/transcoding. Never mutates input bytes. */
internal object MmsOutgoingJpegCompressor {
    private const val MAX_INPUT_BYTES = 5 * 1024 * 1024
    private const val MAX_SOURCE_PIXELS = 20_000_000L
    private const val MAX_DECODED_PIXELS = 4_000_000L
    private val dimensions = intArrayOf(1600, 1280, 1024, 800, 640, 480, 320, 240)
    private val qualities = intArrayOf(85, 75, 65, 55, 45)

    sealed interface Result {
        data class Compressed(val bytes: ByteArray) : Result
        data class Rejected(val reason: String) : Result
    }

    fun compress(input: ByteArray, budgetBytes: Int): Result {
        if (budgetBytes <= 0) return Result.Rejected("INVALID_BUDGET")
        if (Thread.currentThread().isInterrupted) return Result.Rejected("INTERRUPTED")
        if (input.size > MAX_INPUT_BYTES) return Result.Rejected("INPUT_TOO_LARGE")
        if (input.size < 4 || input[0] != 0xff.toByte() || input[1] != 0xd8.toByte() ||
            input[2] != 0xff.toByte() || input[input.size - 2] != 0xff.toByte() ||
            input.last() != 0xd9.toByte()) return Result.Rejected("INVALID_JPEG")

        var base: Bitmap? = null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return Result.Rejected("INVALID_DIMENSIONS")
            if (width.toLong() * height > MAX_SOURCE_PIXELS) return Result.Rejected("PIXEL_LIMIT_EXCEEDED")
            val orientation = ExifInterface(ByteArrayInputStream(input))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            if (orientation !in 0..8) return Result.Rejected("INVALID_EXIF")
            var sample = 1
            fun sampledPixels() = ((width.toLong() + sample - 1) / sample) * ((height.toLong() + sample - 1) / sample)
            while ((width.toLong() + sample - 1) / sample > 1600 ||
                (height.toLong() + sample - 1) / sample > 1600 || sampledPixels() > MAX_DECODED_PIXELS) {
                sample *= 2
            }
            // Legacy Android cannot reliably report partial entropy decoding. Preserve v22
            // pass-through behavior there, but never introduce best-effort lossy transcoding.
            if (Build.VERSION.SDK_INT < 28 && input.size > budgetBytes) {
                return Result.Rejected("STRICT_DECODER_UNAVAILABLE")
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false
            }
            // One bounds read and one sampled pixel decode, including pass-through validation.
            val orientationBaked = Build.VERSION.SDK_INT >= 28
            base = if (orientationBaked) Api28.decodeChecked(input, sample)
                else BitmapFactory.decodeByteArray(input, 0, input.size, options)
            if (base == null) return Result.Rejected("DECODE_FAILED")
            if (base.width.toLong() * base.height > MAX_DECODED_PIXELS) return Result.Rejected("PIXEL_LIMIT_EXCEEDED")
            if (Thread.currentThread().isInterrupted) return Result.Rejected("INTERRUPTED")
            if (input.size <= budgetBytes) return Result.Compressed(input)

            // budgetBytes is now strictly below input.size <= 5 MiB. Reuse one bounded buffer.
            val output = BudgetOutput(budgetBytes)
            var previousWidth = -1
            var previousHeight = -1
            for (dimension in dimensions) {
                val ratio = minOf(1.0, dimension.toDouble() / maxOf(base.width, base.height))
                val targetWidth = maxOf(1, (base.width * ratio).toInt())
                val targetHeight = maxOf(1, (base.height * ratio).toInt())
                if (targetWidth == previousWidth && targetHeight == previousHeight) continue
                previousWidth = targetWidth; previousHeight = targetHeight
                var scaled: Bitmap? = null
                try {
                    scaled = Bitmap.createScaledBitmap(base, targetWidth, targetHeight, true)
                    for (quality in qualities) {
                        if (Thread.currentThread().isInterrupted) return Result.Rejected("INTERRUPTED")
                        output.reset()
                        val encoded = try { scaled.compress(Bitmap.CompressFormat.JPEG, quality, output) }
                            catch (_: BudgetExceeded) { false }
                        if (encoded && !output.exceeded && output.count > 0) {
                            return Result.Compressed(output.bytes())
                        }
                    }
                } finally { if (scaled !== base) scaled?.recycle() }
            }
            return Result.Rejected("CANNOT_FIT_BUDGET")
        } catch (_: OutOfMemoryError) {
            return Result.Rejected("MEMORY_LIMIT")
        } catch (_: IOException) {
            return Result.Rejected("INVALID_JPEG")
        } catch (_: RuntimeException) {
            return Result.Rejected("COMPRESSION_FAILED")
        } finally { base?.recycle() }
    }

    private object Api28 {
        @android.annotation.TargetApi(28)
        fun decodeChecked(input: ByteArray, sample: Int): Bitmap =
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(input))) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                decoder.setTargetSampleSize(sample)
                decoder.setOnPartialImageListener { false }
                // Native libjpeg may recover entropy damage without flagging partial input.
                // The dedicated failing regression records this known qualification blocker.
                // ImageDecoder itself applies EXIF rotations and mirrors, including transpose.
            }
    }

    private class BudgetExceeded : IOException()
    private class BudgetOutput(limit: Int) : OutputStream() {
        private val buffer = ByteArray(limit)
        var count = 0; private set
        var exceeded = false; private set
        fun reset() { count = 0; exceeded = false }
        fun bytes(): ByteArray = buffer.copyOf(count)
        override fun write(value: Int) {
            if (count >= buffer.size) { exceeded = true; throw BudgetExceeded() }
            buffer[count++] = value.toByte()
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length < 0 || offset < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
            if (length > buffer.size - count) { exceeded = true; throw BudgetExceeded() }
            bytes.copyInto(buffer, count, offset, offset + length); count += length
        }
    }
}
