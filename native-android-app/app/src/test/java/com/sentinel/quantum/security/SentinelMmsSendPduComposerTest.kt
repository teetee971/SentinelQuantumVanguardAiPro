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
}
