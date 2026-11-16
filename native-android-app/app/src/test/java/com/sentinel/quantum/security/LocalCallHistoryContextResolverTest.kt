package com.sentinel.quantum.security

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCallHistoryContextResolverTest {
    @Test
    fun onlyCanonicalMatchingRowsContributeToBoundedContext() {
        val rows = listOf(
            entry("06 12 34 56 78", CallLog.Calls.INCOMING_TYPE, 3_000L),
            entry("+33612345678", CallLog.Calls.MISSED_TYPE, 2_000L),
            entry("+33699999999", CallLog.Calls.MISSED_TYPE, 1_000L)
        )

        val context = LocalCallHistoryContextResolver.fromEntries(
            rawNumber = "+33 6 12 34 56 78",
            entries = rows
        )

        assertEquals(2, context.recentCalls)
        assertEquals(1, context.missedCalls)
        assertEquals(3_000L, context.lastInteractionEpochMillis)
    }

    @Test
    fun invalidTargetProducesEmptyLocalContext() {
        val context = LocalCallHistoryContextResolver.fromEntries(
            rawNumber = "not-a-number",
            entries = listOf(entry("0612345678", CallLog.Calls.MISSED_TYPE, 2_000L))
        )

        assertEquals(0, context.recentCalls)
        assertEquals(0, context.missedCalls)
        assertNull(context.lastInteractionEpochMillis)
    }

    @Test
    fun contextNeverExposesRawNumbers() {
        val context = LocalCallHistoryContextResolver.fromEntries(
            rawNumber = "+33612345678",
            entries = listOf(entry("+33612345678", CallLog.Calls.OUTGOING_TYPE, 4_000L))
        )

        assertEquals(
            setOf("recentCalls", "missedCalls", "lastInteractionEpochMillis"),
            context::class.java.declaredFields
                .map { it.name }
                .filterNot { it.startsWith("$") }
                .toSet()
        )
    }

    private fun entry(number: String?, type: Int, date: Long) = SystemCallLogReader.Entry(
        number = number,
        type = type,
        dateMillis = date,
        durationSeconds = 0L
    )
}
