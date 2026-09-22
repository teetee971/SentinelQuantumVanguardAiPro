package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSnapshotDiffTest {

    private fun state(
        at: Long,
        devices: Set<String> = emptySet(),
        services: Set<NetworkSnapshotService> = emptySet(),
        edges: Set<NetworkSnapshotTopologyEdge> = emptySet(),
        risks: Map<String, Int> = emptyMap()
    ) = NetworkSnapshotState(
        observedAtMs = at,
        deviceFingerprints = devices,
        services = services,
        topologyEdges = edges,
        riskScores = risks
    )

    @Test
    fun detectsAddedAndRemovedDevices() {
        val result = NetworkSnapshotDiffEngine.compare(
            previous = state(
                at = 1_000L,
                devices = setOf("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
            ),
            current = state(
                at = 2_000L,
                devices = setOf("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc")
            )
        )!!

        assertEquals(setOf("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"), result.addedDevices)
        assertEquals(setOf("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), result.removedDevices)
    }

    @Test
    fun detectsOpenedAndClosedServices() {
        val oldService = NetworkSnapshotService("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NetworkSnapshotProtocol.TCP, 80)
        val keptService = NetworkSnapshotService("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NetworkSnapshotProtocol.TCP, 443)
        val newService = NetworkSnapshotService("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NetworkSnapshotProtocol.UDP, 5353)

        val result = NetworkSnapshotDiffEngine.compare(
            previous = state(
                at = 1_000L,
                services = setOf(oldService, keptService)
            ),
            current = state(
                at = 2_000L,
                services = setOf(keptService, newService)
            )
        )!!

        assertEquals(setOf(newService), result.openedServices)
        assertEquals(setOf(oldService), result.closedServices)
    }

    @Test
    fun canonicalizesPeerEdgesBeforeDiffing() {
        val previousEdge = NetworkSnapshotTopologyEdge(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            NetworkSnapshotRelation.PEERS_WITH
        )
        val currentEdgeReversed = NetworkSnapshotTopologyEdge(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            NetworkSnapshotRelation.PEERS_WITH
        )

        val result = NetworkSnapshotDiffEngine.compare(
            previous = state(at = 1_000L, edges = setOf(previousEdge)),
            current = state(at = 2_000L, edges = setOf(currentEdgeReversed))
        )!!

        assertTrue(result.addedTopologyEdges.isEmpty())
        assertTrue(result.removedTopologyEdges.isEmpty())
    }

    @Test
    fun reportsRiskDirectionAndMaterialIncrease() {
        val result = NetworkSnapshotDiffEngine.compare(
            previous = state(
                at = 1_000L,
                risks = mapOf(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" to 20,
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" to 80
                )
            ),
            current = state(
                at = 2_000L,
                risks = mapOf(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" to 50,
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" to 60
                )
            ),
            materialRiskDelta = 20
        )!!

        assertEquals(1, result.riskIncreases.size)
        assertEquals(30, result.riskIncreases.single().delta)
        assertEquals(1, result.riskDecreases.size)
        assertEquals(-20, result.riskDecreases.single().delta)
        assertEquals(1, result.materialRiskIncreases.size)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", result.materialRiskIncreases.single().subjectFingerprint)
    }

    @Test
    fun newOrMissingRiskScoreIsNotInventedAsDelta() {
        val result = NetworkSnapshotDiffEngine.compare(
            previous = state(
                at = 1_000L,
                risks = mapOf("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" to 20)
            ),
            current = state(
                at = 2_000L,
                risks = mapOf("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" to 90)
            )
        )!!

        assertTrue(result.riskIncreases.isEmpty())
        assertTrue(result.riskDecreases.isEmpty())
    }

    @Test
    fun malformedItemsAreRejectedWithoutBreakingValidDiff() {
        val result = NetworkSnapshotDiffEngine.compare(
            previous = state(
                at = 1_000L,
                devices = setOf("AA:BB:CC:DD:EE:FF", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                services = setOf(
                    NetworkSnapshotService("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NetworkSnapshotProtocol.TCP, 443),
                    NetworkSnapshotService("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NetworkSnapshotProtocol.TCP, 0)
                ),
                risks = mapOf(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" to 20,
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" to 101
                )
            ),
            current = state(
                at = 2_000L,
                devices = setOf("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            )
        )!!

        assertEquals(3, result.rejectedItems)
        assertTrue(result.addedDevices.isEmpty())
        assertTrue(result.removedDevices.isEmpty())
    }

    @Test
    fun invalidChronologyOrThresholdFailsClosed() {
        assertNull(
            NetworkSnapshotDiffEngine.compare(
                previous = state(at = 2_000L),
                current = state(at = 1_000L)
            )
        )
        assertNull(
            NetworkSnapshotDiffEngine.compare(
                previous = state(at = 1_000L),
                current = state(at = 2_000L),
                materialRiskDelta = 0
            )
        )
    }

    @Test
    fun serviceFingerprintNormalizationIsCaseInsensitive() {
        val upper = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".uppercase()
        val previousService = NetworkSnapshotService(
            upper,
            NetworkSnapshotProtocol.TCP,
            443
        )
        val currentService = NetworkSnapshotService(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            NetworkSnapshotProtocol.TCP,
            443
        )

        val result = NetworkSnapshotDiffEngine.compare(
            previous = state(at = 1_000L, services = setOf(previousService)),
            current = state(at = 2_000L, services = setOf(currentService))
        )!!

        assertTrue(result.openedServices.isEmpty())
        assertTrue(result.closedServices.isEmpty())
    }
}
