package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsPartHeaderInteropTest {
    @Test
    fun contentDispositionBeforeReferencesDoesNotHideCidOrLocation() {
        val payload = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x01)
        val header = byteArrayOf(
            0x9e.toByte(), // image/jpeg
            0xc5.toByte(), 0x01, 0x82.toByte(), // Content-Disposition: inline
            0xc0.toByte()
        ) + "<img1>".toByteArray(Charsets.US_ASCII) + byteArrayOf(
            0x00,
            0x8e.toByte()
        ) + "photo.jpg".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00)
        val pdu = byteArrayOf(0x00, 0x01) + encodedPart(header, payload)
        val envelope = envelope(bodyOffset = 1)

        val result = SentinelMmsPduDecoder.decodeRetrieveBody(pdu, envelope)
        assertTrue(result is MmsPduDecoder.DecodeResult.Decoded)
        val part = (result as MmsPduDecoder.DecodeResult.Decoded).parts.single()
        assertEquals("<img1>", part.contentId)
        assertEquals("photo.jpg", part.contentLocation)
    }

    @Test
    fun malformedContentDispositionLengthFailsClosed() {
        val payload = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x01)
        val header = byteArrayOf(
            0x9e.toByte(), // image/jpeg
            0xc5.toByte(), 0x1e, 0x82.toByte() // claims 30 bytes, only one remains
        )
        val pdu = byteArrayOf(0x00, 0x01) + encodedPart(header, payload)

        val result = SentinelMmsPduDecoder.decodeRetrieveBody(pdu, envelope(bodyOffset = 1))
        assertTrue(result is MmsPduDecoder.DecodeResult.Rejected)
        assertEquals(
            "INVALID_MULTIPART_BODY",
            (result as MmsPduDecoder.DecodeResult.Rejected).reason
        )
    }

    private fun envelope(bodyOffset: Int) = MmsRetrieveEnvelopeParser.Envelope(
        messageType = 0x84,
        mmsVersion = 0x12,
        dateSeconds = 1_700_000_000L,
        senderDisposition = MmsRetrieveEnvelopeParser.SenderDisposition.ADDRESS,
        senderAddress = "+590690123456",
        messageId = "msg-part-header",
        transactionId = "tx-part-header",
        contentType = "application/vnd.wap.multipart.related",
        bodyOffset = bodyOffset
    )

    private fun encodedPart(header: ByteArray, payload: ByteArray): ByteArray {
        require(header.size < 128 && payload.size < 128)
        return byteArrayOf(header.size.toByte(), payload.size.toByte()) + header + payload
    }
}
