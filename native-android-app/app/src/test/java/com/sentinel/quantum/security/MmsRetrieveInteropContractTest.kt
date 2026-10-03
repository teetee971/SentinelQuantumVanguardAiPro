package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsRetrieveInteropContractTest {
    @Test
    fun commonStandardOperatorHeadersRemainBoundedAndAccepted() {
        val body = multipartTextBody("operator")
        val optional = byteArrayOf(
            0x83.toByte(), // Content-Location: Text-String
            'h'.code.toByte(), 't'.code.toByte(), 't'.code.toByte(), 'p'.code.toByte(),
            's'.code.toByte(), ':'.code.toByte(), '/'.code.toByte(), '/'.code.toByte(),
            'm'.code.toByte(), '/'.code.toByte(), '1'.code.toByte(), 0x00,
            0x8e.toByte(), // Message-Size: Long-Integer
            0x01, 0x40,
            0x88.toByte(), // Expiry: Value-length + relative-token + Long-Integer
            0x03, 0x81.toByte(), 0x01, 0x3c,
            0x86.toByte(), 0x80.toByte(), // Delivery-Report
            0x90.toByte(), 0x81.toByte(), // Read-Reply
            0x99.toByte(), 0x80.toByte() // Retrieve-Status
        )
        val pdu = retrieveConf(optional, body)

        val result = MmsRetrieveEnvelopeParser.parse(pdu)
        assertTrue(result is MmsRetrieveEnvelopeParser.Result.Accepted)
        val envelope = (result as MmsRetrieveEnvelopeParser.Result.Accepted).envelope
        assertEquals("+590690123456", envelope.senderAddress)
        assertEquals("msg-operator", envelope.messageId)
        assertTrue(pdu.copyOfRange(envelope.bodyOffset, pdu.size).contentEquals(body))
    }

    @Test
    fun malformedOrDuplicateStandardHeadersFailClosed() {
        val body = multipartTextBody("x")
        val invalidExpiry = byteArrayOf(
            0x88.toByte(), 0x03, 0x82.toByte(), 0x01, 0x01
        )
        assertRejected(retrieveConf(invalidExpiry, body), "INVALID_TIME_HEADER")

        val duplicateLocation = byteArrayOf(
            0x83.toByte(), 'a'.code.toByte(), 0x00,
            0x83.toByte(), 'b'.code.toByte(), 0x00
        )
        assertRejected(retrieveConf(duplicateLocation, body), "DUPLICATE_CONTENT_LOCATION")
    }

    @Test
    fun anchoredDecoderStartsAtEnvelopeBodyNotPayloadHeaderLikeBytes() {
        val jpeg = byteArrayOf(
            0xff.toByte(), 0xd8.toByte(), 0xff.toByte(),
            0x84.toByte(), // looks like X-Mms-Content-Type but is just image payload
            0x23, 0x01, 0x01, 0x01
        )
        val body = multipartImageBody(jpeg)
        val pdu = retrieveConf(ByteArray(0), body)
        val envelope = (
            MmsRetrieveEnvelopeParser.parse(pdu) as MmsRetrieveEnvelopeParser.Result.Accepted
            ).envelope

        val decoded = SentinelMmsPduDecoder.decodeRetrieveBody(pdu, envelope)
        assertTrue(decoded is MmsPduDecoder.DecodeResult.Decoded)
        val parts = (decoded as MmsPduDecoder.DecodeResult.Decoded).parts
        assertEquals(1, parts.size)
        assertEquals("image/jpeg", parts.single().mimeType)
        assertTrue(parts.single().payload.contentEquals(jpeg))
    }

    @Test
    fun anchoredDecoderRejectsForgedBodyOffset() {
        val body = multipartTextBody("safe")
        val pdu = retrieveConf(ByteArray(0), body)
        val envelope = (
            MmsRetrieveEnvelopeParser.parse(pdu) as MmsRetrieveEnvelopeParser.Result.Accepted
            ).envelope
        val forged = envelope.copy(bodyOffset = pdu.size)

        val decoded = SentinelMmsPduDecoder.decodeRetrieveBody(pdu, forged)
        assertTrue(decoded is MmsPduDecoder.DecodeResult.Rejected)
        assertEquals(
            "INVALID_BODY_OFFSET",
            (decoded as MmsPduDecoder.DecodeResult.Rejected).reason
        )
    }

    private fun assertRejected(pdu: ByteArray, reason: String) {
        val result = MmsRetrieveEnvelopeParser.parse(pdu)
        assertTrue(result is MmsRetrieveEnvelopeParser.Result.Rejected)
        assertEquals(reason, (result as MmsRetrieveEnvelopeParser.Result.Rejected).reason)
    }

    private fun retrieveConf(optionalHeaders: ByteArray, body: ByteArray): ByteArray {
        val sender = "+590690123456/TYPE=PLMN".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        return byteArrayOf(
            0x8c.toByte(), 0x84.toByte(), // M-Retrieve.conf
            0x8d.toByte(), 0x92.toByte(), // MMS 1.2
            0x85.toByte(), 0x04, 0x65, 0x53, 0xf1.toByte(), 0x00, // Date
            0x89.toByte(), (1 + sender.size).toByte(), 0x80.toByte()
        ) + sender + byteArrayOf(
            0x8b.toByte(),
            'm'.code.toByte(), 's'.code.toByte(), 'g'.code.toByte(), '-'.code.toByte(),
            'o'.code.toByte(), 'p'.code.toByte(), 'e'.code.toByte(), 'r'.code.toByte(),
            'a'.code.toByte(), 't'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 0x00
        ) + optionalHeaders + byteArrayOf(
            0x84.toByte(), 0xa3.toByte() // multipart/mixed
        ) + body
    }

    private fun multipartTextBody(text: String): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)
        require(payload.size < 128)
        return byteArrayOf(
            0x01, 0x01, payload.size.toByte(), 0x83.toByte()
        ) + payload
    }

    private fun multipartImageBody(payload: ByteArray): ByteArray {
        require(payload.size < 128)
        return byteArrayOf(
            0x01, 0x01, payload.size.toByte(), 0x9e.toByte() // image/jpeg
        ) + payload
    }
}
