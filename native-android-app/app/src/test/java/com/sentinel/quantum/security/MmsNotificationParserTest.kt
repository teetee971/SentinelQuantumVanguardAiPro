package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmsNotificationParserTest {
    @Test fun parsesSingleSafeCarrierLocation() {
        val location = "http://mmsc.example.test/mms/123"
        val pdu = byteArrayOf(
            0x8c.toByte(),
            0x82.toByte(),
            0x83.toByte()
        ) + location.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)

        assertEquals(location, MmsNotificationParser.parse(pdu)?.contentLocation)
    }

    @Test fun rejectsNonNotificationMessage() {
        val location = "http://mmsc.example.test/mms/123"
        val pdu = byteArrayOf(
            0x8c.toByte(),
            0x84.toByte(),
            0x83.toByte()
        ) + location.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)

        assertNull(MmsNotificationParser.parse(pdu))
    }

    @Test fun rejectsUnsafeOrAmbiguousLocations() {
        fun pdu(location: String) = byteArrayOf(
            0x8c.toByte(),
            0x82.toByte(),
            0x83.toByte()
        ) + location.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)

        assertNull(MmsNotificationParser.parse(pdu("file:///tmp/mms")))
        assertNull(MmsNotificationParser.parse(pdu("http://user:pass@mmsc.example.test/mms")))

        val first = "http://mmsc.example.test/a"
        val second = "http://mmsc.example.test/b"
        val ambiguous = byteArrayOf(0x8c.toByte(), 0x82.toByte(), 0x83.toByte()) +
            first.toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0x83.toByte()) +
            second.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        assertNull(MmsNotificationParser.parse(ambiguous))
    }
}
