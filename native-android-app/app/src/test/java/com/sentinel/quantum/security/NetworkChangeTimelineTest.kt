package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkChangeTimelineTest {

    @Test
    fun buildsReverseChronologyAndCountsSeverity() {
        val result = NetworkChangeTimeline.build(
            events = listOf(
                NetworkTimelineEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkTimelineEventKind.FIRST_SEEN,
                    NetworkTimelineSource.ANDROID_WIFI,
                    NetworkTimelineSeverity.INFO,
                    1_000L,
                    "Premier appareil observé"
                ),
                NetworkTimelineEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkTimelineEventKind.FLOW_ANOMALY,
                    NetworkTimelineSource.FLOW_ANALYZER,
                    NetworkTimelineSeverity.HIGH,
                    2_000L,
                    "Destination inhabituelle"
                ),
                NetworkTimelineEvent(
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    NetworkTimelineEventKind.TOPOLOGY_CHANGE,
                    NetworkTimelineSource.TOPOLOGY_ENGINE,
                    NetworkTimelineSeverity.WARNING,
                    1_500L,
                    "Nouvelle relation observée"
                )
            ),
            nowMs = 3_000L,
            retentionMs = 10_000L
        )

        assertEquals(3, result.acceptedEvents.size)
        assertEquals(2_000L, result.acceptedEvents.first().observedAtMs)
        assertEquals(1, result.highSeverityCount)
        assertEquals(1, result.warningCount)
        assertEquals(1_000L, result.firstObservedAtMs)
        assertEquals(2_000L, result.lastObservedAtMs)
    }

    @Test
    fun duplicateSignalInsideWindowIsSuppressed() {
        val first = NetworkTimelineEvent(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            NetworkTimelineEventKind.COVERT_DEVICE_HINT,
            NetworkTimelineSource.ANDROID_BLE,
            NetworkTimelineSeverity.WARNING,
            10_000L,
            "Indice BLE compatible"
        )
        val second = first.copy(observedAtMs = 20_000L)

        val result = NetworkChangeTimeline.build(
            events = listOf(first, second),
            nowMs = 30_000L,
            retentionMs = 60_000L
        )

        assertEquals(1, result.acceptedEvents.size)
        assertEquals(1, result.rejectedEvents)
    }

    @Test
    fun sameSignalFromDifferentSourceIsKept() {
        val result = NetworkChangeTimeline.build(
            events = listOf(
                NetworkTimelineEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkTimelineEventKind.IDENTITY_CHANGE,
                    NetworkTimelineSource.LOCAL_AGENT,
                    NetworkTimelineSeverity.WARNING,
                    10_000L,
                    "Identité modifiée"
                ),
                NetworkTimelineEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkTimelineEventKind.IDENTITY_CHANGE,
                    NetworkTimelineSource.USER_CONFIRMED,
                    NetworkTimelineSeverity.WARNING,
                    20_000L,
                    "Identité modifiée"
                )
            ),
            nowMs = 30_000L,
            retentionMs = 60_000L
        )

        assertEquals(2, result.acceptedEvents.size)
        assertEquals(0, result.rejectedEvents)
    }

    @Test
    fun oldEventsArePrunedByRetentionWindow() {
        val result = NetworkChangeTimeline.build(
            events = listOf(
                NetworkTimelineEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkTimelineEventKind.FIRST_SEEN,
                    NetworkTimelineSource.ANDROID_WIFI,
                    NetworkTimelineSeverity.INFO,
                    1_000L,
                    "Ancienne observation"
                ),
                NetworkTimelineEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkTimelineEventKind.RETURNED,
                    NetworkTimelineSource.ANDROID_WIFI,
                    NetworkTimelineSeverity.INFO,
                    95_000L,
                    "Retour récent"
                )
            ),
            nowMs = 100_000L,
            retentionMs = 10_000L
        )

        assertEquals(1, result.acceptedEvents.size)
        assertEquals(1, result.prunedEvents)
    }

    @Test
    fun rawMacFutureTimestampAndBlankSummaryAreRejected() {
        val result = NetworkChangeTimeline.build(
            events = listOf(
                NetworkTimelineEvent(
                    "AA:BB:CC:DD:EE:FF",
                    NetworkTimelineEventKind.FIRST_SEEN,
                    NetworkTimelineSource.ANDROID_WIFI,
                    NetworkTimelineSeverity.INFO,
                    1_000L,
                    "MAC brute"
                ),
                NetworkTimelineEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkTimelineEventKind.FIRST_SEEN,
                    NetworkTimelineSource.ANDROID_WIFI,
                    NetworkTimelineSeverity.INFO,
                    50_000L,
                    "Future"
                ),
                NetworkTimelineEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkTimelineEventKind.FIRST_SEEN,
                    NetworkTimelineSource.ANDROID_WIFI,
                    NetworkTimelineSeverity.INFO,
                    1_000L,
                    "   "
                )
            ),
            nowMs = 10_000L,
            retentionMs = 20_000L
        )

        assertTrue(result.acceptedEvents.isEmpty())
        assertEquals(3, result.rejectedEvents)
    }

    @Test
    fun summaryIsNormalizedAndBounded() {
        val result = NetworkChangeTimeline.build(
            events = listOf(
                NetworkTimelineEvent(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkTimelineEventKind.RISK_SCORE_CHANGED,
                    NetworkTimelineSource.RISK_DOSSIER,
                    NetworkTimelineSeverity.WARNING,
                    1_000L,
                    "  Risque   augmenté   " + "x".repeat(300)
                )
            ),
            nowMs = 2_000L,
            retentionMs = 10_000L
        )

        val summary = result.acceptedEvents.single().summary
        assertTrue(summary.startsWith("Risque augmenté"))
        assertEquals(160, summary.length)
    }
}
