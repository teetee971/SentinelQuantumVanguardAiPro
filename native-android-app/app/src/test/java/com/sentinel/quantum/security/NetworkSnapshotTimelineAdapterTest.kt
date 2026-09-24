package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSnapshotTimelineAdapterTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)

    @Test fun mapsPresenceTopologyAndRiskWithoutRawIdentifiers() {
        val edge = NetworkSnapshotTopologyEdge(a, b, NetworkSnapshotRelation.ATTACHED_TO_GATEWAY)
        val increase = NetworkRiskDelta(a, 20, 55)
        val diff = NetworkSnapshotDiff(
            previousObservedAtMs = 1_000L,
            currentObservedAtMs = 2_000L,
            addedDevices = setOf(a),
            removedDevices = setOf(b),
            openedServices = emptySet(),
            closedServices = emptySet(),
            addedTopologyEdges = setOf(edge),
            removedTopologyEdges = emptySet(),
            riskIncreases = listOf(increase),
            riskDecreases = emptyList(),
            materialRiskIncreases = listOf(increase),
            rejectedItems = 0
        )

        val events = NetworkSnapshotTimelineAdapter.toEvents(diff)
        assertTrue(events.any { it.subjectFingerprint == a && it.kind == NetworkTimelineEventKind.FIRST_SEEN })
        assertTrue(events.any { it.subjectFingerprint == b && it.kind == NetworkTimelineEventKind.DISAPPEARED })
        assertEquals(2, events.count { it.kind == NetworkTimelineEventKind.TOPOLOGY_CHANGE })
        assertTrue(events.any { it.kind == NetworkTimelineEventKind.RISK_SCORE_CHANGED && it.severity == NetworkTimelineSeverity.HIGH })
        assertTrue(events.all { it.source == NetworkTimelineSource.TOPOLOGY_ENGINE })
    }

    @Test fun materialThresholdControlsTimelineSeverity() {
        val increase = NetworkRiskDelta(a, 40, 45)
        val diff = NetworkSnapshotDiff(1L, 2L, emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), listOf(increase), emptyList(), emptyList(), 0)
        val event = NetworkSnapshotTimelineAdapter.toEvents(diff).single()
        assertEquals(NetworkTimelineSeverity.WARNING, event.severity)
    }

    @Test fun serviceDiffDoesNotInventTimelineSemantics() {
        val service = NetworkSnapshotService(a, NetworkSnapshotProtocol.TCP, 443)
        val diff = NetworkSnapshotDiff(1L, 2L, emptySet(), emptySet(), setOf(service), emptySet(), emptySet(), emptySet(), emptyList(), emptyList(), emptyList(), 0)
        assertTrue(NetworkSnapshotTimelineAdapter.toEvents(diff).isEmpty())
    }
}