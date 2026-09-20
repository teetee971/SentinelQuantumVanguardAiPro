package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsDecodeBoundaryTest {
    @Test fun acceptsBoundedSafeDecodedPart() {
        val result = MmsDecodeBoundary.validate(listOf(
            MmsDecodeBoundary.DecodedPart("image/jpeg", "photo.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1))
        ))
        assertTrue(result is MmsDecodeBoundary.Result.Accepted)
        val accepted = result as MmsDecodeBoundary.Result.Accepted
        assertEquals("image/jpeg", accepted.parts.single().mimeType)
    }

    @Test fun rejectsSpoofedPayloadSignatureBeforeRendering() {
        val result = MmsDecodeBoundary.validate(listOf(
            MmsDecodeBoundary.DecodedPart("image/jpeg", "photo.jpg", "not-a-jpeg".toByteArray())
        ))
        assertTrue(result is MmsDecodeBoundary.Result.Rejected)
        assertEquals("CONTENT_SIGNATURE_MISMATCH", (result as MmsDecodeBoundary.Result.Rejected).reason)
    }

    @Test fun rejectsUnsafeMetadataBeforeRendering() {
        val result = MmsDecodeBoundary.validate(listOf(
            MmsDecodeBoundary.DecodedPart("image/jpeg", "photo.png", ByteArray(128))
        ))
        assertTrue(result is MmsDecodeBoundary.Result.Rejected)
        assertEquals("MIME_EXTENSION_MISMATCH", (result as MmsDecodeBoundary.Result.Rejected).reason)
    }

    @Test fun rejectsEmptyAndTooManyParts() {
        assertEquals("INVALID_PART_COUNT", (MmsDecodeBoundary.validate(emptyList()) as MmsDecodeBoundary.Result.Rejected).reason)
        val many = List(33) { MmsDecodeBoundary.DecodedPart("text/plain", "x.txt", byteArrayOf(1)) }
        assertEquals("INVALID_PART_COUNT", (MmsDecodeBoundary.validate(many) as MmsDecodeBoundary.Result.Rejected).reason)
    }

    @Test fun rejectsAggregateDecodedPayloadBomb() {
        val parts = listOf(
            MmsDecodeBoundary.DecodedPart("image/jpeg", "a.jpg", ByteArray(8 * 1024 * 1024).also { it[0]=0xFF.toByte(); it[1]=0xD8.toByte(); it[2]=0xFF.toByte() }),
            MmsDecodeBoundary.DecodedPart("image/jpeg", "b.jpg", ByteArray(8 * 1024 * 1024).also { it[0]=0xFF.toByte(); it[1]=0xD8.toByte(); it[2]=0xFF.toByte() }),
            MmsDecodeBoundary.DecodedPart("text/plain", "c.txt", byteArrayOf(1))
        )
        assertEquals("DECODED_MESSAGE_TOO_LARGE", (MmsDecodeBoundary.validate(parts) as MmsDecodeBoundary.Result.Rejected).reason)
    }
}
