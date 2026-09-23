package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelMmsPduDecoderTest {
    @Test fun decodesBoundedTextMultipart() {
        val pdu = MmsSafePreviewReadiness.multipartFixture(
            partContentType = 0x83,
            payload = "bonjour".toByteArray()
        )
        val result = SentinelMmsPduDecoder.decode(pdu)
        assertTrue(result is MmsPduDecoder.DecodeResult.Decoded)
        val decoded = result as MmsPduDecoder.DecodeResult.Decoded
        assertEquals(1, decoded.parts.size)
        assertEquals("text/plain", decoded.parts.single().mimeType)
        assertEquals("bonjour", decoded.parts.single().payload.toString(Charsets.UTF_8))
    }

    @Test fun pipelineRejectsSpoofedImagePayload() {
        val pdu = MmsSafePreviewReadiness.multipartFixture(
            partContentType = 0x9e,
            payload = "not-jpeg".toByteArray()
        )
        val result = MmsDecodePipeline.decodeAndValidate(pdu, SentinelMmsPduDecoder)
        assertEquals(
            "CONTENT_SIGNATURE_MISMATCH",
            (result as MmsDecodePipeline.Result.Rejected).reason
        )
    }

    @Test fun malformedMultipartFailsClosed() {
        val malformed = byteArrayOf(
            0x84.toByte(),
            0xa3.toByte(),
            0x01,
            0x7f,
            0x01,
            0x83.toByte()
        )
        val result = SentinelMmsPduDecoder.decode(malformed)
        assertTrue(result is MmsPduDecoder.DecodeResult.Rejected)
    }

    @Test fun readinessSelfTestCoversRuntimeDecoderAndBoundary() {
        assertTrue(MmsSafePreviewReadiness.softwareValidated)
    }
}
