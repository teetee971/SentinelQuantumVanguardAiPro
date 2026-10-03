package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingMmsProjectionPlanTest {
    @Test
    fun explicitSenderAndProtocolIdentityProduceProviderReadyPlan() {
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)
        val result = IncomingMmsProjectionPlan.build(
            digestHex = "a".repeat(64),
            envelope = envelope(sender = "+590690123456", messageId = "msg-1", transactionId = "tx-1"),
            safeParts = listOf(
                safe("text/plain", "bonjour".toByteArray()),
                safe("image/jpeg", image)
            ),
            subscriptionId = 2
        )

        assertTrue(result is IncomingMmsProjectionPlan.Result.Ready)
        val plan = (result as IncomingMmsProjectionPlan.Result.Ready).plan
        assertEquals("+590690123456", plan.sender)
        assertEquals("msg-1", plan.messageId)
        assertEquals("tx-1", plan.transactionId)
        assertEquals(2, plan.subscriptionId)
        assertEquals(7L + image.size, plan.messageSizeBytes)
        assertFalse(plan.textOnly)
        assertEquals(2, plan.parts.size)
        assertEquals("bonjour", (plan.parts.first() as IncomingMmsProjectionPlan.Part.Text).text)
    }

    @Test
    fun senderMustBeProtocolExplicitNotInvented() {
        for (disposition in listOf(
            MmsRetrieveEnvelopeParser.SenderDisposition.ABSENT,
            MmsRetrieveEnvelopeParser.SenderDisposition.INSERT_ADDRESS_TOKEN
        )) {
            val result = IncomingMmsProjectionPlan.build(
                "b".repeat(64),
                envelope(sender = null, senderDisposition = disposition, messageId = "msg-2"),
                listOf(safe("text/plain", "x".toByteArray())),
                subscriptionId = 0
            )
            assertQuarantined(result, "SENDER_NOT_EXPLICIT")
        }
    }

    @Test
    fun atLeastOneRealProtocolRecoveryIdentityIsRequired() {
        val result = IncomingMmsProjectionPlan.build(
            "c".repeat(64),
            envelope(sender = "+33612345678", messageId = null, transactionId = null),
            listOf(safe("text/plain", "x".toByteArray())),
            subscriptionId = 0
        )
        assertQuarantined(result, "MISSING_RECOVERY_IDENTITY")
    }

    @Test
    fun transactionIdAloneIsAcceptedWithoutManufacturingMessageId() {
        val result = IncomingMmsProjectionPlan.build(
            "d".repeat(64),
            envelope(sender = "sender@example.test", messageId = null, transactionId = "tx-only"),
            listOf(safe("text/plain", "mail".toByteArray())),
            subscriptionId = 1
        )
        assertTrue(result is IncomingMmsProjectionPlan.Result.Ready)
        val plan = (result as IncomingMmsProjectionPlan.Result.Ready).plan
        assertEquals(null, plan.messageId)
        assertEquals("tx-only", plan.transactionId)
    }

    @Test
    fun malformedUtf8TextRemainsQuarantinedInsteadOfBeingSilentlyReplaced() {
        val result = IncomingMmsProjectionPlan.build(
            "e".repeat(64),
            envelope(sender = "+33612345678", messageId = "msg-utf8"),
            listOf(safe("text/plain", byteArrayOf(0xC3.toByte(), 0x28))),
            subscriptionId = 0
        )
        assertQuarantined(result, "TEXT_CHARSET_UNSUPPORTED")
    }

    @Test
    fun invalidSubscriptionAndPrivateIdentityFailClosed() {
        val parts = listOf(safe("text/plain", "x".toByteArray()))
        assertQuarantined(
            IncomingMmsProjectionPlan.build(
                "not-a-digest",
                envelope(sender = "+33612345678", messageId = "msg-3"),
                parts,
                0
            ),
            "INVALID_PRIVATE_IDENTITY"
        )
        assertQuarantined(
            IncomingMmsProjectionPlan.build(
                "f".repeat(64),
                envelope(sender = "+33612345678", messageId = "msg-3"),
                parts,
                -1
            ),
            "INVALID_SUBSCRIPTION"
        )
    }

    @Test
    fun textOnlyTruthComesFromActuallyProjectedParts() {
        val result = IncomingMmsProjectionPlan.build(
            "1".repeat(64),
            envelope(sender = "+33612345678", messageId = "msg-text"),
            listOf(
                safe("text/plain", "a".toByteArray()),
                safe("text/plain", "b".toByteArray())
            ),
            0
        ) as IncomingMmsProjectionPlan.Result.Ready
        assertTrue(result.plan.textOnly)
        assertEquals(2L, result.plan.messageSizeBytes)
    }

    @Test
    fun relatedAndAlternativeStayPrivateUntilPresentationMetadataIsPreserved() {
        val parts = listOf(safe("text/plain", "x".toByteArray()))
        for (contentType in listOf(
            "application/vnd.wap.multipart.related",
            "application/vnd.wap.multipart.alternative"
        )) {
            val result = IncomingMmsProjectionPlan.build(
                "2".repeat(64),
                envelope(
                    sender = "+33612345678",
                    messageId = "msg-rich",
                    contentType = contentType
                ),
                parts,
                0
            )
            assertQuarantined(result, "PRESENTATION_METADATA_NOT_PRESERVED")
        }
    }

    private fun envelope(
        sender: String?,
        senderDisposition: MmsRetrieveEnvelopeParser.SenderDisposition =
            MmsRetrieveEnvelopeParser.SenderDisposition.ADDRESS,
        messageId: String? = null,
        transactionId: String? = null,
        contentType: String = "application/vnd.wap.multipart.mixed"
    ) = MmsRetrieveEnvelopeParser.Envelope(
        messageType = 0x84,
        mmsVersion = 0x12,
        dateSeconds = 1_700_000_000L,
        senderDisposition = senderDisposition,
        senderAddress = sender,
        messageId = messageId,
        transactionId = transactionId,
        contentType = contentType,
        bodyOffset = 20
    )

    private fun safe(mime: String, payload: ByteArray) =
        MmsDecodeBoundary.SafePart(mime, null, payload)

    private fun assertQuarantined(result: IncomingMmsProjectionPlan.Result, reason: String) {
        assertTrue(result is IncomingMmsProjectionPlan.Result.Quarantined)
        assertEquals(reason, (result as IncomingMmsProjectionPlan.Result.Quarantined).reason)
    }
}
