package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingMmsProjectionAddressTest {
    @Test
    fun providerPlanCarriesValidatedRecipientHeadersWithoutInventingAddresses() {
        val envelope = MmsRetrieveEnvelopeParser.Envelope(
            messageType = 0x84,
            mmsVersion = 0x12,
            dateSeconds = 1_700_000_000L,
            senderDisposition = MmsRetrieveEnvelopeParser.SenderDisposition.ADDRESS,
            senderAddress = "+590690111111",
            messageId = "msg-addresses",
            transactionId = "tx-addresses",
            toAddresses = listOf("+590690222222", "+590690333333"),
            ccAddresses = listOf("friend@example.test"),
            bccAddresses = listOf("hidden@example.test"),
            contentType = "application/vnd.wap.multipart.mixed",
            bodyOffset = 20
        )
        val result = IncomingMmsProjectionPlan.build(
            digestHex = "3".repeat(64),
            envelope = envelope,
            safeParts = listOf(
                MmsDecodeBoundary.SafePart("text/plain", null, "bonjour".toByteArray())
            ),
            subscriptionId = 1
        )

        assertTrue(result is IncomingMmsProjectionPlan.Result.Ready)
        val plan = (result as IncomingMmsProjectionPlan.Result.Ready).plan
        assertEquals(envelope.toAddresses, plan.toAddresses)
        assertEquals(envelope.ccAddresses, plan.ccAddresses)
        assertEquals(envelope.bccAddresses, plan.bccAddresses)
    }
}
