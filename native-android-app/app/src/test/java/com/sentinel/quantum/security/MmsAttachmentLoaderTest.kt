package com.sentinel.quantum.security

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsAttachmentLoaderTest {
    @Test fun boundedReaderAcceptsExactLimitAndRejectsOverflow() {
        val exact = ByteArray(64) { it.toByte() }
        assertArrayEquals(
            exact,
            MmsAttachmentLoader.readBounded(ByteArrayInputStream(exact), 64)
        )
        assertNull(
            MmsAttachmentLoader.readBounded(ByteArrayInputStream(ByteArray(65)), 64)
        )
    }

    @Test fun boundedReaderRejectsInvalidBounds() {
        assertNull(MmsAttachmentLoader.readBounded(ByteArrayInputStream(byteArrayOf(1)), 0))
        assertNull(
            MmsAttachmentLoader.readBounded(
                ByteArrayInputStream(byteArrayOf(1)),
                Int.MAX_VALUE.toLong() + 1L
            )
        )
    }

    @Test fun mimeAliasesNormalizeOnlyToSupportedImageTypes() {
        assertEquals("image/jpeg", MmsAttachmentFormat.normalizeMimeType("image/jpg"))
        assertEquals("image/jpeg", MmsAttachmentFormat.normalizeMimeType(" IMAGE/PJPEG "))
        assertEquals("image/png", MmsAttachmentFormat.normalizeMimeType("image/x-png"))
        assertEquals("image/gif", MmsAttachmentFormat.normalizeMimeType("image/gif"))
        assertEquals("image/webp", MmsAttachmentFormat.normalizeMimeType("image/webp"))
        assertNull(MmsAttachmentFormat.normalizeMimeType("application/octet-stream"))
        assertNull(MmsAttachmentFormat.normalizeMimeType("text/plain"))
        assertNull(MmsAttachmentFormat.normalizeMimeType(null))
    }

    @Test fun jpegMagicMustMatchDeclaredType() {
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x00)
        assertTrue(MmsAttachmentFormat.matchesPayload("image/jpeg", jpeg))
        assertFalse(MmsAttachmentFormat.matchesPayload("image/png", jpeg))
    }

    @Test fun pngGifAndWebpMagicAreRecognized() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
        )
        val gif = "GIF89a-data".toByteArray(Charsets.US_ASCII)
        val webp = "RIFF0000WEBPpayload".toByteArray(Charsets.US_ASCII)
        assertTrue(MmsAttachmentFormat.matchesPayload("image/png", png))
        assertTrue(MmsAttachmentFormat.matchesPayload("image/gif", gif))
        assertTrue(MmsAttachmentFormat.matchesPayload("image/webp", webp))
        assertFalse(MmsAttachmentFormat.matchesPayload("image/jpeg", webp))
    }

    @Test fun truncatedOrUnknownPayloadsFailClosed() {
        assertFalse(MmsAttachmentFormat.matchesPayload("image/jpeg", byteArrayOf(0xff.toByte())))
        assertFalse(MmsAttachmentFormat.matchesPayload("image/png", byteArrayOf(0x89.toByte(), 0x50)))
        assertFalse(MmsAttachmentFormat.matchesPayload("image/webp", "RIFF".toByteArray()))
        assertFalse(MmsAttachmentFormat.matchesPayload("image/bmp", ByteArray(32)))
    }
}
