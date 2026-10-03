package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsRetrieveEnvelopeParserTest {
    @Test
    fun parsesRetrieveConfWithoutManufacturingIdentity() {
        val body = multipartTextBody("bonjour")
        val pdu = retrieveConf(
            sender = "+590690123456/TYPE=PLMN",
            messageId = "msg-123",
            transactionId = "tx-123",
            dateSeconds = 1_700_000_000L,
            body = body
        )

        val result = MmsRetrieveEnvelopeParser.parse(pdu)
        assertTrue(result is MmsRetrieveEnvelopeParser.Result.Accepted)
        val envelope = (result as MmsRetrieveEnvelopeParser.Result.Accepted).envelope
        assertEquals(0x84, envelope.messageType)
        assertEquals(0x12, envelope.mmsVersion)
        assertEquals(1_700_000_000L, envelope.dateSeconds)
        assertEquals(MmsRetrieveEnvelopeParser.SenderDisposition.ADDRESS, envelope.senderDisposition)
        assertEquals("+590690123456", envelope.senderAddress)
        assertEquals("msg-123", envelope.messageId)
        assertEquals("tx-123", envelope.transactionId)
        assertEquals("application/vnd.wap.multipart.mixed", envelope.contentType)
        assertTrue(envelope.bodyOffset in 1 until pdu.size)
        assertTrue(pdu.copyOfRange(envelope.bodyOffset, pdu.size).contentEquals(body))
    }

    @Test
    fun acceptsProtocolOptionalSenderAndIdentifiersAsAbsent() {
        val pdu = retrieveConf(
            sender = null,
            messageId = null,
            transactionId = null,
            dateSeconds = 1_700_000_001L,
            body = multipartTextBody("sans identité inventée")
        )

        val envelope = (MmsRetrieveEnvelopeParser.parse(pdu) as MmsRetrieveEnvelopeParser.Result.Accepted).envelope
        assertEquals(MmsRetrieveEnvelopeParser.SenderDisposition.ABSENT, envelope.senderDisposition)
        assertNull(envelope.senderAddress)
        assertNull(envelope.messageId)
        assertNull(envelope.transactionId)
    }

    @Test
    fun preservesInsertAddressTokenAsProtocolStateNotPhoneNumber() {
        val pdu = retrieveConf(
            sender = INSERT_ADDRESS_SENTINEL,
            messageId = null,
            transactionId = "tx-2",
            dateSeconds = 1_700_000_002L,
            body = multipartTextBody("x")
        )

        val envelope = (MmsRetrieveEnvelopeParser.parse(pdu) as MmsRetrieveEnvelopeParser.Result.Accepted).envelope
        assertEquals(MmsRetrieveEnvelopeParser.SenderDisposition.INSERT_ADDRESS_TOKEN, envelope.senderDisposition)
        assertNull(envelope.senderAddress)
    }

    @Test
    fun skipsBoundedUtf8SubjectWithoutTreatingItsBytesAsHeaders() {
        val subjectPayload = byteArrayOf(
            0xea.toByte(), // UTF-8 MIBEnum=106 as WSP short-integer.
            0xc3.toByte(), 0xa9.toByte(), 0x00
        )
        val optionalSubject = byteArrayOf(0x96.toByte(), subjectPayload.size.toByte()) + subjectPayload
        val pdu = retrieveConf(
            sender = "+33612345678",
            messageId = null,
            transactionId = null,
            dateSeconds = 1_700_000_003L,
            body = multipartTextBody("ok"),
            optionalHeaders = optionalSubject
        )

        assertTrue(MmsRetrieveEnvelopeParser.parse(pdu) is MmsRetrieveEnvelopeParser.Result.Accepted)
    }

    @Test
    fun rejectsWrongMessageTypeAndUnsupportedExtensionHeaders() {
        val valid = retrieveConf(
            sender = "+33612345678",
            messageId = null,
            transactionId = null,
            dateSeconds = 1_700_000_004L,
            body = multipartTextBody("ok")
        )
        val wrongType = valid.copyOf().also { it[1] = 0x82.toByte() }
        assertRejected(wrongType, "NOT_RETRIEVE_CONF")

        val extension = byteArrayOf(
            0x8c.toByte(), 0x84.toByte(),
            0x8d.toByte(), 0x92.toByte(),
            0x85.toByte(), 0x01, 0x01,
            'X'.code.toByte(), '-'.code.toByte(), 'A'.code.toByte(), 0x00,
            'v'.code.toByte(), 0x00,
            0x84.toByte(), 0xa3.toByte()
        ) + multipartTextBody("x")
        assertRejected(extension, "APPLICATION_HEADER_UNSUPPORTED")
    }

    @Test
    fun duplicateAndMalformedSecurityRelevantHeadersFailClosed() {
        val duplicateDate = byteArrayOf(
            0x8c.toByte(), 0x84.toByte(),
            0x8d.toByte(), 0x92.toByte(),
            0x85.toByte(), 0x01, 0x01,
            0x85.toByte(), 0x01, 0x02,
            0x84.toByte(), 0xa3.toByte()
        ) + multipartTextBody("x")
        assertRejected(duplicateDate, "DUPLICATE_DATE")

        val oversizedLongInteger = byteArrayOf(
            0x8c.toByte(), 0x84.toByte(),
            0x8d.toByte(), 0x92.toByte(),
            0x85.toByte(), 0x09
        ) + ByteArray(9) { 1 }
        assertRejected(oversizedLongInteger, "INVALID_DATE")
    }

    @Test
    fun requiresRetrieveConfMandatoryEnvelopeFieldsAndMultipartBody() {
        val noDate = byteArrayOf(
            0x8c.toByte(), 0x84.toByte(),
            0x8d.toByte(), 0x92.toByte(),
            0x84.toByte(), 0xa3.toByte()
        ) + multipartTextBody("x")
        assertRejected(noDate, "MISSING_DATE")

        val noBody = byteArrayOf(
            0x8c.toByte(), 0x84.toByte(),
            0x8d.toByte(), 0x92.toByte(),
            0x85.toByte(), 0x01, 0x01,
            0x84.toByte(), 0xa3.toByte()
        )
        assertRejected(noBody, "MISSING_MULTIPART_BODY")
    }

    private fun assertRejected(pdu: ByteArray, reason: String) {
        val result = MmsRetrieveEnvelopeParser.parse(pdu)
        assertTrue(result is MmsRetrieveEnvelopeParser.Result.Rejected)
        assertEquals(reason, (result as MmsRetrieveEnvelopeParser.Result.Rejected).reason)
    }

    private fun retrieveConf(
        sender: String?,
        messageId: String?,
        transactionId: String?,
        dateSeconds: Long,
        body: ByteArray,
        optionalHeaders: ByteArray = ByteArray(0)
    ): ByteArray {
        val headers = ArrayList<Byte>()
        headers += 0x8c.toByte()
        headers += 0x84.toByte() // M-Retrieve.conf
        headers += 0x8d.toByte()
        headers += 0x92.toByte() // MMS 1.2 short integer
        headers += 0x85.toByte()
        headers += longInteger(dateSeconds).toList()

        if (sender != null) {
            headers += 0x89.toByte()
            if (sender == INSERT_ADDRESS_SENTINEL) {
                headers += 0x01.toByte()
                headers += 0x81.toByte()
            } else {
                val encoded = sender.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
                headers += (1 + encoded.size).toByte()
                headers += 0x80.toByte()
                headers += encoded.toList()
            }
        }

        if (messageId != null) {
            headers += 0x8b.toByte()
            headers += messageId.toByteArray(Charsets.US_ASCII).toList()
            headers += 0x00.toByte()
        }
        if (transactionId != null) {
            headers += 0x98.toByte()
            headers += transactionId.toByteArray(Charsets.US_ASCII).toList()
            headers += 0x00.toByte()
        }
        headers += optionalHeaders.toList()
        headers += 0x84.toByte()
        headers += 0xa3.toByte() // application/vnd.wap.multipart.mixed
        return headers.toByteArray() + body
    }

    private fun longInteger(value: Long): ByteArray {
        require(value >= 0L)
        if (value == 0L) return byteArrayOf(0x01, 0x00)
        var remaining = value
        val bytes = ArrayList<Byte>()
        while (remaining > 0) {
            bytes.add(0, (remaining and 0xff).toByte())
            remaining = remaining ushr 8
        }
        return byteArrayOf(bytes.size.toByte()) + bytes.toByteArray()
    }

    private fun multipartTextBody(text: String): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)
        require(payload.size < 128)
        return byteArrayOf(
            0x01, // part count
            0x01, // part header length
            payload.size.toByte(),
            0x83.toByte() // text/plain
        ) + payload
    }

    companion object {
        private const val INSERT_ADDRESS_SENTINEL = "<insert-address-token>"
    }
}
