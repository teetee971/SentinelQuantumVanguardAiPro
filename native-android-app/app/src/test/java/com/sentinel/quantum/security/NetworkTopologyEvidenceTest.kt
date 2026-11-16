package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTopologyEvidenceTest {

    @Test
    fun acceptsExplicitRouterRelationshipWithProvenance() {
        val result = NetworkTopologyEvidence.normalize(
            listOf(
                NetworkTopologyEdge(
                    fromFingerprint = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    toFingerprint = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    relation = NetworkTopologyRelation.ATTACHED_TO_GATEWAY,
                    source = NetworkTopologyEvidenceSource.ROUTER_TABLE,
                    observedAtMs = 1_000L,
                    confidencePercent = 90
                )
            )
        )

        assertEquals(1, result.acceptedEdges.size)
        assertEquals(0, result.rejectedEdges)
        assertEquals(setOf("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"), result.nodeFingerprints)
    }

    @Test
    fun rejectsRawMacStyleAndSelfRelationships() {
        val result = NetworkTopologyEvidence.normalize(
            listOf(
                NetworkTopologyEdge(
                    "AA:BB:CC:DD:EE:FF",
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    NetworkTopologyRelation.ATTACHED_TO_GATEWAY,
                    NetworkTopologyEvidenceSource.DHCP_LEASE,
                    1_000L,
                    80
                ),
                NetworkTopologyEdge(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkTopologyRelation.MANAGED_BY,
                    NetworkTopologyEvidenceSource.USER_CONFIRMED,
                    2_000L,
                    100
                )
            )
        )

        assertTrue(result.acceptedEdges.isEmpty())
        assertEquals(2, result.rejectedEdges)
    }

    @Test
    fun peerRelationshipIsCanonicalRegardlessOfDirection() {
        val result = NetworkTopologyEvidence.normalize(
            listOf(
                NetworkTopologyEdge(
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    NetworkTopologyRelation.PEERS_WITH,
                    NetworkTopologyEvidenceSource.MESH_CONTROL_PLANE,
                    1_000L,
                    95
                )
            )
        )

        val edge = result.acceptedEdges.single()
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", edge.fromFingerprint)
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", edge.toFingerprint)
    }

    @Test
    fun duplicateEvidenceIsSuppressedButDifferentSourcesRemain() {
        val result = NetworkTopologyEvidence.normalize(
            listOf(
                NetworkTopologyEdge(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    NetworkTopologyRelation.BRIDGED_BY,
                    NetworkTopologyEvidenceSource.LOCAL_AGENT,
                    1_000L,
                    80
                ),
                NetworkTopologyEdge(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    NetworkTopologyRelation.BRIDGED_BY,
                    NetworkTopologyEvidenceSource.LOCAL_AGENT,
                    2_000L,
                    90
                ),
                NetworkTopologyEdge(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    NetworkTopologyRelation.BRIDGED_BY,
                    NetworkTopologyEvidenceSource.USER_CONFIRMED,
                    3_000L,
                    100
                )
            )
        )

        assertEquals(2, result.acceptedEdges.size)
        assertEquals(1, result.rejectedEdges)
    }

    @Test
    fun multipleObservedRelationshipsBuildNodeSetWithoutInferringMore() {
        val result = NetworkTopologyEvidence.normalize(
            listOf(
                NetworkTopologyEdge(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    NetworkTopologyRelation.ATTACHED_TO_GATEWAY,
                    NetworkTopologyEvidenceSource.ROUTER_TABLE,
                    1_000L,
                    90
                ),
                NetworkTopologyEdge(
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                    NetworkTopologyRelation.MANAGED_BY,
                    NetworkTopologyEvidenceSource.USER_CONFIRMED,
                    2_000L,
                    100
                )
            )
        )

        assertEquals(2, result.acceptedEdges.size)
        assertEquals(setOf("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"), result.nodeFingerprints)
    }
}
