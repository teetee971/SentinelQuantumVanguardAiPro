package com.sentinel.quantum.security

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Test

class CallHistoryInsightsTest {
    private fun entry(type: Int, duration: Long = 0L) =
        SystemCallLogReader.Entry(
            number = "+33600000000",
            type = type,
            dateMillis = 1L,
            durationSeconds = duration
        )

    @Test fun summaryCountsOnlyObservedCallLogTypes() {
        val summary = CallHistoryInsights.summarize(
            listOf(
                entry(CallLog.Calls.INCOMING_TYPE, 60),
                entry(CallLog.Calls.OUTGOING_TYPE, 120),
                entry(CallLog.Calls.MISSED_TYPE),
                entry(CallLog.Calls.REJECTED_TYPE),
                entry(CallLog.Calls.BLOCKED_TYPE),
                entry(999, 5)
            )
        )
        assertEquals(6, summary.total)
        assertEquals(1, summary.incoming)
        assertEquals(1, summary.outgoing)
        assertEquals(1, summary.missed)
        assertEquals(1, summary.rejected)
        assertEquals(1, summary.blocked)
        assertEquals(1, summary.other)
        assertEquals(185L, summary.totalDurationSeconds)
    }

    @Test fun durationLabelIsDeterministic() {
        assertEquals("0 s", CallHistoryInsights.durationLabelFr(-5))
        assertEquals("45 s", CallHistoryInsights.durationLabelFr(45))
        assertEquals("2 min 5 s", CallHistoryInsights.durationLabelFr(125))
        assertEquals("1 h 2 min", CallHistoryInsights.durationLabelFr(3_720))
    }
}
