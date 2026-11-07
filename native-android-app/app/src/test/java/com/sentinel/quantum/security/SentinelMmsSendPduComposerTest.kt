package com.sentinel.quantum.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelMmsSendPduComposerTest {
    @Test fun composesDeterministicTextOnlySendRequest() {
        val result = SentinelMmsSendPduComposer.compose(
            destination = "+33612345678",
            transactionId = "sentinel-1",
            text = "bonjour",
            attachments = emptyList()
        )
        assertTrue(result is SentinelMmsSendPduComposer.Result.Composed)
        val pdu = (result as SentinelMmsSendPduComposer.Result.Composed).pdu
        assertArrayEquals(
            byteArrayOf(0x8c.toByte(), 0x80.toByte(), 0x98.toByte()),
            pdu.copyOfRange(0, 3)
        )
        assertTrue(pdu.contains(0x97.toByte()))
        assertTrue(pdu.contains(0x84.toByte()))
        assertTrue(pdu.contains(0xa3.toByte()))
        assertTrue(pdu.toString(Charsets.ISO_8859_1).contains("+33612345678"))
        assertTrue(pdu.toString(Charsets.ISO_8859_1).contains("bonjour"))
    }

    @Test fun uintvarUsesWspContinuationEncoding() {
        assertArrayEquals(byteArrayOf(0x00), SentinelMmsSendPduComposer.encodeUintvar(0))
        assertArrayEquals(byteArrayOf(0x7f), SentinelMmsSendPduComposer.encodeUintvar(127))
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x00), SentinelMmsSendPduComposer.encodeUintvar(128))
        assertArrayEquals(byteArrayOf(0xff.toByte(), 0x7f), SentinelMmsSendPduComposer.encodeUintvar(16383))
    }

    @Test fun rejectsUnsupportedMimeBeforeEncoding() {
        val result = SentinelMmsSendPduComposer.compose(
            "+33612345678", "sentinel-2", "",
            listOf(SentinelMmsSendPduComposer.Part("application/pdf", byteArrayOf(1)))
        )
        assertEquals(
            "UNSUPPORTED_MIME",
            (result as SentinelMmsSendPduComposer.Result.Rejected).reason
        )
    }

    @Test fun rejectsEmptyPayload() {
        val result = SentinelMmsSendPduComposer.compose("+33612345678", "sentinel-3", "", emptyList())
        assertEquals("EMPTY_MMS", (result as SentinelMmsSendPduComposer.Result.Rejected).reason)
    }

    @Test fun supportsWebpAsExtensionMediaWithoutHiddenApis() {
        val result = SentinelMmsSendPduComposer.compose(
            "+33612345678", "sentinel-4", "",
            listOf(SentinelMmsSendPduComposer.Part("image/webp", byteArrayOf(1, 2, 3)))
        )
        assertTrue(result is SentinelMmsSendPduComposer.Result.Composed)
        val pdu = (result as SentinelMmsSendPduComposer.Result.Composed).pdu
        assertTrue(pdu.toString(Charsets.ISO_8859_1).contains("image/webp"))
    }

    // Without a transport-size check these requests were accepted then failed in Android.
    @Test fun rejectsPayloadAboveSelectedCarrierLimit() {
        val result = composeForCarrier(700_000, 614_400)
        assertEquals("MMS_CARRIER_SIZE_EXCEEDED", (result as? SentinelMmsSendPduComposer.Result.Rejected)?.reason)
    }

    @Test fun countsHeadersRatherThanOnlyImageBytes() {
        val result = composeForCarrier(614_400, 614_400)
        assertEquals("MMS_CARRIER_SIZE_EXCEEDED", (result as? SentinelMmsSendPduComposer.Result.Rejected)?.reason)
    }

    @Test fun usesSelectedCarrierLimitInsteadOfAnotherSimLimit() {
        assertTrue(composeForCarrier(483_388, 614_400) is SentinelMmsSendPduComposer.Result.Composed)
        assertEquals("MMS_CARRIER_SIZE_EXCEEDED", (composeForCarrier(483_388, 307_200) as? SentinelMmsSendPduComposer.Result.Rejected)?.reason)
    }

    @Test fun unavailableCarrierLimitCannotApproveTransport() {
        for (limit in listOf(0, -1)) {
            assertEquals("MMS_CARRIER_CONFIG_UNAVAILABLE", (composeForCarrier(1, limit) as? SentinelMmsSendPduComposer.Result.Rejected)?.reason)
        }
    }

    @Test fun acceptsExactPduBoundaryAndRejectsOneByteLess() {
        val composed = composeForCarrier(100, 614_400) as SentinelMmsSendPduComposer.Result.Composed
        assertTrue(composeForCarrier(100, composed.pdu.size) is SentinelMmsSendPduComposer.Result.Composed)
        assertEquals("MMS_CARRIER_SIZE_EXCEEDED", (composeForCarrier(100, composed.pdu.size - 1) as? SentinelMmsSendPduComposer.Result.Rejected)?.reason)
    }

    @Test fun carrierLimitDoesNotBypassLocalSafetyLimit() {
        assertEquals("INVALID_PART_SIZE", (composeForCarrier(5 * 1024 * 1024 + 1, Int.MAX_VALUE) as? SentinelMmsSendPduComposer.Result.Rejected)?.reason)
    }

    private fun composeForCarrier(payloadBytes: Int, carrierLimit: Int) =
        SentinelMmsSendPduComposer.compose(
            "+33612345678", "carrier-test", "test",
            listOf(SentinelMmsSendPduComposer.Part("image/jpeg", ByteArray(payloadBytes))),
            maxPduBytes = carrierLimit
        )

    @Test fun countedSizeMatchesActualEncoderAcrossUintvarBoundaries() {
        for(size in listOf(1,127,128,16383,16384))for(mime in listOf("image/jpeg","image/png","image/webp")) {
            val parts=listOf(SentinelMmsSendPduComposer.Part(mime,ByteArray(size)))
            val encoded=SentinelMmsSendPduComposer.compose("+33612345678","count-case","texte é",parts) as SentinelMmsSendPduComposer.Result.Composed
            assertEquals(encoded.pdu.size,SentinelMmsSendPduComposer.measuredSize("+33612345678","count-case","texte é",parts))
        }
    }
}
