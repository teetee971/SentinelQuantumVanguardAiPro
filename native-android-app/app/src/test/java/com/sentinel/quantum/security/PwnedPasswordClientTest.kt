package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class PwnedPasswordClientTest {
    @Test
    fun sha1IsUppercaseAndDeterministic() {
        assertEquals(
            "5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8",
            PwnedPasswordClient.sha1Hex("password")
        )
    }

    @Test
    fun rangeResponseReturnsMatchingOccurrenceCount() {
        val suffix = "1E4C9B93F3F0682250B6CF8331B7EE68FD8"
        val response = """
            00000000000000000000000000000000000:2
            $suffix:12345
            FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:1
        """.trimIndent()
        assertEquals(12345L, PwnedPasswordClient.findOccurrenceCount(response, suffix))
    }

    @Test
    fun unmatchedSuffixReturnsZero() {
        assertEquals(
            0L,
            PwnedPasswordClient.findOccurrenceCount(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:4",
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
            )
        )
    }
    @Test
    fun boundedReaderAcceptsExactLimitAndRejectsOverflow() {
        val exact = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(exact, BoundedInputReader.read(ByteArrayInputStream(exact), exact.size))
        assertNull(BoundedInputReader.read(ByteArrayInputStream(exact), exact.size - 1))
    }
}
