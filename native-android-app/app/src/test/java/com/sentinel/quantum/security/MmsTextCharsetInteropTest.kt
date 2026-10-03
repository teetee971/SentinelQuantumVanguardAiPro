package com.sentinel.quantum.security

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsTextCharsetInteropTest {
    @Test
    fun ucs2ContentTypeParameterSurvivesDecodeBoundaryAndProjection() {
        val payload = byteArrayOf(0x00, 0x48, 0x00, 0x69) // "Hi" in UTF-16BE / UCS-2
        val contentType = byteArrayOf(
            0x05, // five bytes in the general-form Content-Type value
            0x83.toByte(), // text/plain
            0x81.toByte(), // charset parameter
            0x02, 0x03, 0xe8.toByte() // long-integer MIB enum 1000 (UCS-2)
        )
        val body = byteArrayOf(
            0x01, // one part
            contentType.size.toByte(),
            payload.size.toByte()
        ) + contentType + payload
        val pdu = byteArrayOf(0x00) + body
        val envelope = envelope(bodyOffset = 1)

        val decoded = SentinelMmsPduDecoder.decodeRetrieveBody(pdu, envelope)
        assertTrue(decoded is MmsPduDecoder.DecodeResult.Decoded)
        val decodedPart = (decoded as MmsPduDecoder.DecodeResult.Decoded).parts.single()
        assertEquals(MmsTextCharset.UCS2, decodedPart.charsetMibEnum)

        val boundary = MmsDecodeBoundary.validate(listOf(decodedPart))
        assertTrue(boundary is MmsDecodeBoundary.Result.Accepted)
        val safe = (boundary as MmsDecodeBoundary.Result.Accepted).parts
        assertEquals(MmsTextCharset.UCS2, safe.single().charsetMibEnum)

        val planned = IncomingMmsProjectionPlan.build(
            digestHex = "8".repeat(64),
            envelope = envelope,
            safeParts = safe,
            subscriptionId = 0
        )
        assertTrue(planned is IncomingMmsProjectionPlan.Result.Ready)
        val text = (
            (planned as IncomingMmsProjectionPlan.Result.Ready).plan.parts.single()
                as IncomingMmsProjectionPlan.Part.Text
            ).text
        assertEquals("Hi", text)
    }

    @Test
    fun internationalCharsetAllowlistIsStrict() {
        val japanese = "日本"
        val shiftJis = japanese.toByteArray(Charset.forName("Shift_JIS"))
        assertEquals(japanese, MmsTextCharset.decode(shiftJis, MmsTextCharset.SHIFT_JIS)?.text)
        assertNull(MmsTextCharset.decode(byteArrayOf(0x41), 0x1234))
    }

    private fun envelope(bodyOffset: Int) = MmsRetrieveEnvelopeParser.Envelope(
        messageType = 0x84,
        mmsVersion = 0x12,
        dateSeconds = 1_700_000_000L,
        senderDisposition = MmsRetrieveEnvelopeParser.SenderDisposition.ADDRESS,
        senderAddress = "+590690123456",
        messageId = "msg-charset",
        transactionId = "tx-charset",
        contentType = "application/vnd.wap.multipart.mixed",
        bodyOffset = bodyOffset
    )
}
