package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSnapshotDiffTest {
    private val a="a".repeat(64); private val b="b".repeat(64); private val c="c".repeat(64)
    private fun state(at:Long,devices:Set<String> = emptySet(),services:Set<NetworkSnapshotService> = emptySet(),edges:Set<NetworkSnapshotTopologyEdge> = emptySet(),risks:Map<String,Int> = emptyMap())=NetworkSnapshotState(at,devices,services,edges,risks)
    @Test fun detectsAddedAndRemovedDevices(){val r=NetworkSnapshotDiffEngine.compare(state(1_000L,setOf(a,b)),state(2_000L,setOf(b,c)))!!;assertEquals(setOf(c),r.addedDevices);assertEquals(setOf(a),r.removedDevices)}
    @Test fun detectsOpenedAndClosedServices(){val old=NetworkSnapshotService(a,NetworkSnapshotProtocol.TCP,80);val keep=NetworkSnapshotService(a,NetworkSnapshotProtocol.TCP,443);val fresh=NetworkSnapshotService(a,NetworkSnapshotProtocol.UDP,5353);val r=NetworkSnapshotDiffEngine.compare(state(1_000L,services=setOf(old,keep)),state(2_000L,services=setOf(keep,fresh)))!!;assertEquals(setOf(fresh),r.openedServices);assertEquals(setOf(old),r.closedServices)}
    @Test fun canonicalizesPeerEdges(){val x=NetworkSnapshotTopologyEdge(a,b,NetworkSnapshotRelation.PEERS_WITH);val y=NetworkSnapshotTopologyEdge(b,a,NetworkSnapshotRelation.PEERS_WITH);val r=NetworkSnapshotDiffEngine.compare(state(1_000L,edges=setOf(x)),state(2_000L,edges=setOf(y)))!!;assertTrue(r.addedTopologyEdges.isEmpty());assertTrue(r.removedTopologyEdges.isEmpty())}
    @Test fun reportsRiskDirectionAndMaterialIncrease(){val r=NetworkSnapshotDiffEngine.compare(state(1_000L,risks=mapOf(a to 20,b to 80)),state(2_000L,risks=mapOf(a to 50,b to 60)),20)!!;assertEquals(30,r.riskIncreases.single().delta);assertEquals(-20,r.riskDecreases.single().delta);assertEquals(a,r.materialRiskIncreases.single().subjectFingerprint)}
    @Test fun missingRiskDoesNotInventDelta(){val r=NetworkSnapshotDiffEngine.compare(state(1_000L,risks=mapOf(a to 20)),state(2_000L,risks=mapOf(b to 90)))!!;assertTrue(r.riskIncreases.isEmpty());assertTrue(r.riskDecreases.isEmpty())}
    @Test fun malformedItemsAreRejected(){val r=NetworkSnapshotDiffEngine.compare(state(1_000L,devices=setOf("AA:BB:CC:DD:EE:FF",a),services=setOf(NetworkSnapshotService(a,NetworkSnapshotProtocol.TCP,443),NetworkSnapshotService(a,NetworkSnapshotProtocol.TCP,0)),risks=mapOf(a to 20,b to 101)),state(2_000L,devices=setOf(a)))!!;assertEquals(3,r.rejectedItems);assertTrue(r.addedDevices.isEmpty());assertTrue(r.removedDevices.isEmpty())}
    @Test fun invalidChronologyOrThresholdFailsClosed(){assertNull(NetworkSnapshotDiffEngine.compare(state(2_000L),state(1_000L)));assertNull(NetworkSnapshotDiffEngine.compare(state(1_000L),state(2_000L),0))}
    @Test fun fingerprintNormalizationIsCaseInsensitive(){val upper=NetworkSnapshotService(a.uppercase(),NetworkSnapshotProtocol.TCP,443);val lower=NetworkSnapshotService(a,NetworkSnapshotProtocol.TCP,443);val r=NetworkSnapshotDiffEngine.compare(state(1_000L,services=setOf(upper)),state(2_000L,services=setOf(lower)))!!;assertTrue(r.openedServices.isEmpty());assertTrue(r.closedServices.isEmpty())}
}
