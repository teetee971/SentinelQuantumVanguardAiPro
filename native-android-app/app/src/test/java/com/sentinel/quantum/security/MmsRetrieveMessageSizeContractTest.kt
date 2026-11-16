package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsRetrieveMessageSizeContractTest {
    @Test
    fun parserPreservesProtocolMessageSizeWhenHeaderIsPresent() {
        val pdu = retrieveConf(
            optionalHeaders = byteArrayOf(
                0x8e.toByte(),
                0x02,
                0x04,
                0x00
            )
        )

        val result = MmsRetrieveEnvelopeParser.parse(pdu)
        assertTrue(result is MmsRetrieveEnvelopeParser.Result.Accepted)
        val envelope = (result as MmsRetrieveEnvelopeParser.Result.Accepted).envelope
        assertEquals(1024L, envelope.messageSizeBytes)
    }

    @Test
    fun parserLeavesMessageSizeAbsentInsteadOfInventingProtocolTruth() {
        val result = MmsRetrieveEnvelopeParser.parse(retrieveConf())
        assertTrue(result is MmsRetrieveEnvelopeParser.Result.Accepted)
        assertNull((result as MmsRetrieveEnvelopeParser.Result.Accepted).envelope.messageSizeBytes)
    }

    @Test
    fun projectionPrefersProtocolSizeAndFallsBackOnlyWhenMissing() {
        val safeParts = listOf(
            MmsDecodeBoundary.SafePart("text/plain", null, "abc".toByteArray()),
            MmsDecodeBoundary.SafePart("image/jpeg", null, byteArrayOf(1, 2, 3, 4))
        )
        val protocol = IncomingMmsProjectionPlan.build(
            digestHex = "4".repeat(64),
            envelope = envelope(messageSizeBytes = 4096L),
            safeParts = safeParts,
            subscriptionId = 1
        ) as IncomingMmsProjectionPlan.Result.Ready
        assertEquals(4096L, protocol.plan.messageSizeBytes)

        val fallback = IncomingMmsProjectionPlan.build(
            digestHex = "5".repeat(64),
            envelope = envelope(messageSizeBytes = null),
            safeParts = safeParts,
            subscriptionId = 1
        ) as IncomingMmsProjectionPlan.Result.Ready
        assertEquals(7L, fallback.plan.messageSizeBytes)
    }

    private fun envelope(messageSizeBytes: Long?) = MmsRetrieveEnvelopeParser.Envelope(
        messageType = 0x84,
        mmsVersion = 0x12,
        dateSeconds = 1_700_000_000L,
        senderDisposition = MmsRetrieveEnvelopeParser.SenderDisposition.ADDRESS,
        senderAddress = "+590690111111",
        messageId = "msg-size",
        transactionId = "tx-size",
        contentType = "application/vnd.wap.multipart.mixed",
        bodyOffset = 20,
        messageSizeBytes = messageSizeBytes
    )

    private fun retrieveConf(optionalHeaders: ByteArray = byteArrayOf()): ByteArray =
        byteArrayOf(
            0x8c.toByte(), 0x84.toByte(), // M-Retrieve.conf
            0x8d.toByte(), 0x92.toByte(), // MMS 1.2
            0x85.toByte(), 0x04, 0x65, 0x53, 0xf1.toByte(), 0x00
        ) + optionalHeaders + byteArrayOf(
            0x84.toByte(), 0xa3.toByte(), // multipart/mixed
            0x01, 0x01, 0x01, 0x83.toByte(), 'x'.code.toByte()
        )
}
