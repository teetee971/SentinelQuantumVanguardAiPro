package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsRetrieveAddressContractTest {
    @Test
    fun retrieveConfPreservesAndNormalizesToCcBccAddresses() {
        val optional = byteArrayOf(
            0x97.toByte()
        ) + address("+590690222222/TYPE=PLMN") + byteArrayOf(
            0x97.toByte()
        ) + address("+590690333333/TYPE=PLMN") + byteArrayOf(
            0x82.toByte()
        ) + address("friend@example.test") + byteArrayOf(
            0x81.toByte()
        ) + address("hidden@example.test")

        val result = MmsRetrieveEnvelopeParser.parse(retrieveConf(optional))
        assertTrue(result is MmsRetrieveEnvelopeParser.Result.Accepted)
        val envelope = (result as MmsRetrieveEnvelopeParser.Result.Accepted).envelope

        assertEquals(listOf("+590690222222", "+590690333333"), envelope.toAddresses)
        assertEquals(listOf("friend@example.test"), envelope.ccAddresses)
        assertEquals(listOf("hidden@example.test"), envelope.bccAddresses)
    }

    @Test
    fun malformedAddressHeaderFailsClosedInsteadOfBeingDiscarded() {
        val optional = byteArrayOf(
            0x97.toByte(),
            0x00
        )
        val result = MmsRetrieveEnvelopeParser.parse(retrieveConf(optional))
        assertTrue(result is MmsRetrieveEnvelopeParser.Result.Rejected)
        assertEquals(
            "INVALID_TO_ADDRESS",
            (result as MmsRetrieveEnvelopeParser.Result.Rejected).reason
        )
    }

    private fun retrieveConf(optionalHeaders: ByteArray): ByteArray {
        val sender = "+590690111111/TYPE=PLMN".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        return byteArrayOf(
            0x8c.toByte(), 0x84.toByte(), // M-Retrieve.conf
            0x8d.toByte(), 0x92.toByte(), // MMS 1.2
            0x85.toByte(), 0x04, 0x65, 0x53, 0xf1.toByte(), 0x00, // Date
            0x89.toByte(), (1 + sender.size).toByte(), 0x80.toByte()
        ) + sender + byteArrayOf(
            0x8b.toByte(),
            'm'.code.toByte(), 's'.code.toByte(), 'g'.code.toByte(), '-'.code.toByte(),
            'a'.code.toByte(), 'd'.code.toByte(), 'd'.code.toByte(), 'r'.code.toByte(), 0x00
        ) + optionalHeaders + byteArrayOf(
            0x84.toByte(), 0xa3.toByte(), // multipart/mixed
            0x01, 0x01, 0x01, 0x83.toByte(), 'x'.code.toByte()
        )
    }

    private fun address(value: String): ByteArray =
        value.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
}
