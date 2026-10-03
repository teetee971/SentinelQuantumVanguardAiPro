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

    @Test fun decodesWebpWhenMimeIsEncodedAsTextString() {
        val payload = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0, 0, 0, 0,
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte()
        )
        val mime = "image/webp".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val pdu = byteArrayOf(
            0x84.toByte(), 0xa3.toByte(), 0x01,
            mime.size.toByte(), payload.size.toByte()
        ) + mime + payload
        val result = MmsDecodePipeline.decodeAndValidate(pdu, SentinelMmsPduDecoder)
        assertTrue(result is MmsDecodePipeline.Result.Accepted)
        assertEquals("image/webp", (result as MmsDecodePipeline.Result.Accepted).parts.single().mimeType)
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

    @Test fun decodesWebpWhenMimeIsEncodedAsExtensionMedia() {
        val mime = "image/webp\u0000".toByteArray(Charsets.US_ASCII)
        val payload = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x04, 0x00, 0x00, 0x00,
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte()
        )
        val pdu = byteArrayOf(
            0x84.toByte(),
            0xa3.toByte(),
            0x01,
            mime.size.toByte(),
            payload.size.toByte()
        ) + mime + payload
        val result = MmsDecodePipeline.decodeAndValidate(pdu, SentinelMmsPduDecoder)
        assertTrue(result is MmsDecodePipeline.Result.Accepted)
    }

    @Test fun canonicalBodyOffsetAvoidsCompetingHeaderInterpretation() {
        val fakeMultipartInsideSubject = byteArrayOf(
            0x84.toByte(), 0xa3.toByte(),
            0x01, 0x01, 0x01, 0x83.toByte(), 'x'.code.toByte(), 0x00
        )
        val subjectPayload = byteArrayOf(0xea.toByte()) + fakeMultipartInsideSubject
        val realPayload = "bonjour canonique".toByteArray(Charsets.UTF_8)
        val realBody = byteArrayOf(
            0x01,
            0x01,
            realPayload.size.toByte(),
            0x83.toByte()
        ) + realPayload
        val pdu = byteArrayOf(
            0x8c.toByte(), 0x84.toByte(), // M-Retrieve.conf
            0x8d.toByte(), 0x92.toByte(), // MMS 1.2
            0x85.toByte(), 0x01, 0x01,    // Date=1
            0x96.toByte(), subjectPayload.size.toByte()
        ) + subjectPayload + byteArrayOf(
            0x84.toByte(), 0xa3.toByte()  // real top-level multipart/mixed
        ) + realBody

        val envelope = MmsRetrieveEnvelopeParser.parse(pdu)
        assertTrue(envelope is MmsRetrieveEnvelopeParser.Result.Accepted)
        val bodyOffset = (envelope as MmsRetrieveEnvelopeParser.Result.Accepted).envelope.bodyOffset
        val decoded = SentinelMmsPduDecoder.decodeMultipartBody(pdu, bodyOffset)
        assertTrue(decoded is MmsPduDecoder.DecodeResult.Decoded)
        val part = (decoded as MmsPduDecoder.DecodeResult.Decoded).parts.single()
        assertEquals("text/plain", part.mimeType)
        assertEquals("bonjour canonique", part.payload.toString(Charsets.UTF_8))
    }

    @Test fun canonicalBodyOffsetFailsClosedOnInvalidOrTrailingBody() {
        val pdu = byteArrayOf(
            0x84.toByte(), 0xa3.toByte(),
            0x01, 0x01, 0x01, 0x83.toByte(), 'x'.code.toByte()
        )
        assertEquals(
            "INVALID_BODY_OFFSET",
            (SentinelMmsPduDecoder.decodeMultipartBody(pdu, 0) as MmsPduDecoder.DecodeResult.Rejected).reason
        )

        val withTrailing = pdu + byteArrayOf(0x00)
        assertEquals(
            "MALFORMED_MULTIPART_BODY",
            (SentinelMmsPduDecoder.decodeMultipartBody(withTrailing, 2) as MmsPduDecoder.DecodeResult.Rejected).reason
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