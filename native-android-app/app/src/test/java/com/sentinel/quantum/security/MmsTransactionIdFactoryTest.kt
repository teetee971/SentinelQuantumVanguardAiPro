package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsTransactionIdFactoryTest {
    @Test fun generatedIdsAlwaysFitTheRealPduComposerContract() {
        repeat(256) {
            val transactionId = MmsTransactionIdFactory.create()
            assertTrue(transactionId.isNotBlank())
            assertTrue(transactionId.length <= 40)
            assertTrue(transactionId.all { character -> character.code in 0x21..0x7e })
            assertTrue(MmsProviderJournal.validTransactionId(transactionId))

            val result = SentinelMmsSendPduComposer.compose(
                destination = "+15551234567",
                transactionId = transactionId,
                text = "probe",
                attachments = emptyList()
            )
            assertTrue(result is SentinelMmsSendPduComposer.Result.Composed)
        }
    }

    @Test fun oldPrefixedUuidShapeIsRejectedByTheSharedContract() {
        val oldShape = "sentinel-" + MmsTransactionIdFactory.create()
        assertTrue(oldShape.length > 40)
        assertFalse(MmsProviderJournal.validTransactionId(oldShape))
        assertTrue(
            SentinelMmsSendPduComposer.compose(
                destination = "+15551234567",
                transactionId = oldShape,
                text = "probe",
                attachments = emptyList()
            ) is SentinelMmsSendPduComposer.Result.Rejected
        )
    }
}
