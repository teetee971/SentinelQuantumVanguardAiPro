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
    fun alternativeMultipartStaysPrivateUntilTopLevelPresentationChoiceIsPreserved() {
        val result = IncomingMmsProjectionPlan.build(
            "2".repeat(64),
            envelope(
                sender = "+33612345678",
                messageId = "msg-alt",
                contentType = "application/vnd.wap.multipart.alternative"
            ),
            listOf(safe("text/plain", "x".toByteArray())),
            0
        )
        assertQuarantined(result, "PRESENTATION_METADATA_NOT_PRESERVED")
    }

    @Test
    fun relatedMultipartRequiresOneSmilAndReferencesForEveryMediaPart() {
        val withoutSmil = IncomingMmsProjectionPlan.build(
            "3".repeat(64),
            envelope(
                sender = "+33612345678",
                messageId = "msg-related-1",
                contentType = "application/vnd.wap.multipart.related"
            ),
            listOf(safe("image/jpeg", jpeg(), contentId = "<img1>")),
            0
        )
        assertQuarantined(withoutSmil, "RELATED_SMIL_REQUIRED")

        val withoutReference = IncomingMmsProjectionPlan.build(
            "4".repeat(64),
            envelope(
                sender = "+33612345678",
                messageId = "msg-related-2",
                contentType = "application/vnd.wap.multipart.related"
            ),
            listOf(
                safe("application/smil", "<smil><body/></smil>".toByteArray()),
                safe("image/jpeg", jpeg())
            ),
            0
        )
        assertQuarantined(withoutReference, "RELATED_PART_REFERENCE_REQUIRED")
    }

    @Test
    fun boundedReferencedRelatedPresentationProducesFaithfulProviderPlan() {
        val smil = "<smil><body><img src=\"cid:img1\"/></body></smil>".toByteArray()
        val result = IncomingMmsProjectionPlan.build(
            "5".repeat(64),
            envelope(
                sender = "+33612345678",
                messageId = "msg-related-ok",
                contentType = "application/vnd.wap.multipart.related"
            ),
            listOf(
                safe("application/smil", smil, contentLocation = "presentation.smil"),
                safe("image/jpeg", jpeg(), contentId = "<img1>", contentLocation = "photo.jpg")
            ),
            0
        )

        assertTrue(result is IncomingMmsProjectionPlan.Result.Ready)
        val plan = (result as IncomingMmsProjectionPlan.Result.Ready).plan
        assertFalse(plan.textOnly)
        assertEquals(2, plan.parts.size)
        val smilPart = plan.parts[0] as IncomingMmsProjectionPlan.Part.Smil
        val imagePart = plan.parts[1] as IncomingMmsProjectionPlan.Part.Binary
        assertEquals("presentation.smil", smilPart.contentLocation)
        assertEquals("<img1>", imagePart.contentId)
        assertEquals("photo.jpg", imagePart.contentLocation)
    }

    @Test
    fun messageSizeMustCoverDecodedPayloadAndStayWithinProviderBound() {
        val payload = "abcd".toByteArray()
        val tooSmall = IncomingMmsProjectionPlan.build(
            "6".repeat(64),
            envelope(sender = "+33612345678", messageId = "msg-size-small", messageSizeBytes = 3L),
            listOf(safe("text/plain", payload)),
            0
        )
        assertQuarantined(tooSmall, "INVALID_MESSAGE_SIZE")

        val absurd = IncomingMmsProjectionPlan.build(
            "7".repeat(64),
            envelope(
                sender = "+33612345678",
                messageId = "msg-size-huge",
                messageSizeBytes = 17L * 1024L * 1024L + 1L
            ),
            listOf(safe("text/plain", payload)),
            0
        )
        assertQuarantined(absurd, "INVALID_MESSAGE_SIZE")
    }

    private fun envelope(
        sender: String?,
        senderDisposition: MmsRetrieveEnvelopeParser.SenderDisposition =
            MmsRetrieveEnvelopeParser.SenderDisposition.ADDRESS,
        messageId: String? = null,
        transactionId: String? = null,
        contentType: String = "application/vnd.wap.multipart.mixed",
        messageSizeBytes: Long? = null
    ) = MmsRetrieveEnvelopeParser.Envelope(
        messageType = 0x84,
        mmsVersion = 0x12,
        dateSeconds = 1_700_000_000L,
        senderDisposition = senderDisposition,
        senderAddress = sender,
        messageId = messageId,
        transactionId = transactionId,
        contentType = contentType,
        bodyOffset = 20,
        messageSizeBytes = messageSizeBytes
    )

    private fun safe(
        mime: String,
        payload: ByteArray,
        contentId: String? = null,
        contentLocation: String? = null
    ) = MmsDecodeBoundary.SafePart(mime, null, payload, contentId, contentLocation)

    private fun jpeg() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1)

    private fun assertQuarantined(result: IncomingMmsProjectionPlan.Result, reason: String) {
        assertTrue(result is IncomingMmsProjectionPlan.Result.Quarantined)
        assertEquals(reason, (result as IncomingMmsProjectionPlan.Result.Quarantined).reason)
    }
}
