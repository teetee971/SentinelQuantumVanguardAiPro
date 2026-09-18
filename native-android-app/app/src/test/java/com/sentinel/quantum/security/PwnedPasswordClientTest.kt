package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
