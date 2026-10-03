package com.sentinel.quantum.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OsintPublicationTimeTest {
    private fun formatter() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Test
    fun nonPositiveTimestampUsesUnknownLabel() {
        assertEquals("UNKNOWN", OsintPublicationTime.format(Date(0L), formatter(), "UNKNOWN"))
        assertEquals("UNKNOWN", OsintPublicationTime.format(Date(-1L), formatter(), "UNKNOWN"))
    }

    @Test
    fun positiveTimestampUsesFormatter() {
        assertEquals(
            "2026-10-03 05:00",
            OsintPublicationTime.format(Date(1_759_467_600_000L), formatter(), "UNKNOWN")
        )
    }
}
