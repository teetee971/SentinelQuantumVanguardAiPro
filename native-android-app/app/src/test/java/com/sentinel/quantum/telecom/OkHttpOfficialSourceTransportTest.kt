package com.sentinel.quantum.telecom

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpOfficialSourceTransportTest {
    @Test
    fun boundedReaderReturnsPayloadWhenItFits() {
        val payload = OkHttpOfficialSourceTransport.readBounded(
            ByteArrayInputStream("official".toByteArray()),
            maxBytes = 8
        )

        assertEquals("official", payload.decodeToString())
    }

    @Test
    fun boundedReaderReturnsOversizeSentinelWithoutReadingUnboundedBody() {
        val payload = OkHttpOfficialSourceTransport.readBounded(
            ByteArrayInputStream("official".toByteArray()),
            maxBytes = 3
        )

        assertEquals(4, payload.size)
    }
}
