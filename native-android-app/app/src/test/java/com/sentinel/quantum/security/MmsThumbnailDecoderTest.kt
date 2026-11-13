package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsThumbnailDecoderTest {

    private fun bytes(vararg values: Int): ByteArray =
        values.map { it.toByte() }.toByteArray()

    @Test
    fun acceptsJpegSignature() {
        assertTrue(
            MmsThumbnailDecoder.matchesSignature(
                bytes(0xFF, 0xD8, 0xFF, 0xE0),
                "image/jpeg"
            )
        )
    }

    @Test
    fun acceptsPngSignature() {
        assertTrue(
            MmsThumbnailDecoder.matchesSignature(
                bytes(
                    0x89, 0x50, 0x4E, 0x47,
                    0x0D, 0x0A, 0x1A, 0x0A
                ),
                "image/png"
            )
        )
    }

    @Test
    fun acceptsGifSignatures() {
        for (version in listOf(0x37, 0x39)) {
            assertTrue(
                MmsThumbnailDecoder.matchesSignature(
                    bytes(
                        0x47, 0x49, 0x46,
                        0x38, version, 0x61
                    ),
                    "image/gif"
                )
            )
        }
    }

    @Test
    fun acceptsWebpSignature() {
        assertTrue(
            MmsThumbnailDecoder.matchesSignature(
                bytes(
                    0x52, 0x49, 0x46, 0x46,
                    0, 0, 0, 0,
                    0x57, 0x45, 0x42, 0x50
                ),
                "image/webp"
            )
        )
    }

    @Test
    fun rejectsUnsupportedMime() {
        assertFalse(
            MmsThumbnailDecoder.matchesSignature(
                bytes(0xFF, 0xD8, 0xFF),
                "application/octet-stream"
            )
        )
    }

    @Test
    fun rejectsTruncatedAndMismatchedHeaders() {
        assertFalse(
            MmsThumbnailDecoder.matchesSignature(
                bytes(0xFF, 0xD8),
                "image/jpeg"
            )
        )

        assertFalse(
            MmsThumbnailDecoder.matchesSignature(
                bytes(0xFF, 0xD8, 0xFF),
                "image/png"
            )
        )
    }
}
