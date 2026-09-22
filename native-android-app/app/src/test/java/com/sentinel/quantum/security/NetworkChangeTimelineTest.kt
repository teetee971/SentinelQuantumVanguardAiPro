package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkChangeTimelineTest {
    private val a="a".repeat(64); private val b="b".repeat(64)
    @Test fun buildsReverseChronologyAndCountsSeverity(){
        val r=NetworkChangeTimeline.build(listOf(
            NetworkTimelineEvent(a,NetworkTimelineEventKind.FIRST_SEEN,NetworkTimelineSource.ANDROID_WIFI,NetworkTimelineSeverity.INFO,1_000L,"Premier appareil observé"),
            NetworkTimelineEvent(a,NetworkTimelineEventKind.FLOW_ANOMALY,NetworkTimelineSource.FLOW_ANALYZER,NetworkTimelineSeverity.HIGH,2_000L,"Destination inhabituelle"),
            NetworkTimelineEvent(b,NetworkTimelineEventKind.TOPOLOGY_CHANGE,NetworkTimelineSource.TOPOLOGY_ENGINE,NetworkTimelineSeverity.WARNING,1_500L,"Nouvelle relation observée")
        ),3_000L,10_000L)
        assertEquals(3,r.acceptedEvents.size); assertEquals(2_000L,r.acceptedEvents.first().observedAtMs); assertEquals(1,r.highSeverityCount); assertEquals(1,r.warningCount); assertEquals(1_000L,r.firstObservedAtMs); assertEquals(2_000L,r.lastObservedAtMs)
    }
    @Test fun duplicateSignalInsideWindowIsSuppressed(){
        val first=NetworkTimelineEvent(a,NetworkTimelineEventKind.COVERT_DEVICE_HINT,NetworkTimelineSource.ANDROID_BLE,NetworkTimelineSeverity.WARNING,10_000L,"Indice BLE compatible")
        val r=NetworkChangeTimeline.build(listOf(first,first.copy(observedAtMs=20_000L)),30_000L,60_000L)
        assertEquals(1,r.acceptedEvents.size); assertEquals(1,r.rejectedEvents)
    }
    @Test fun rejectedDuplicateDoesNotAdvanceWindow(){
        val first=NetworkTimelineEvent(a,NetworkTimelineEventKind.COVERT_DEVICE_HINT,NetworkTimelineSource.ANDROID_BLE,NetworkTimelineSeverity.WARNING,10_000L,"Indice BLE compatible")
        val r=NetworkChangeTimeline.build(listOf(first,first.copy(observedAtMs=50_000L),first.copy(observedAtMs=70_000L)),80_000L,80_000L)
        assertEquals(2,r.acceptedEvents.size); assertEquals(1,r.rejectedEvents); assertEquals(listOf(70_000L,10_000L),r.acceptedEvents.map{it.observedAtMs})
    }
    @Test fun sameSignalFromDifferentSourceIsKept(){
        val r=NetworkChangeTimeline.build(listOf(
            NetworkTimelineEvent(a,NetworkTimelineEventKind.IDENTITY_CHANGE,NetworkTimelineSource.LOCAL_AGENT,NetworkTimelineSeverity.WARNING,10_000L,"Identité modifiée"),
            NetworkTimelineEvent(a,NetworkTimelineEventKind.IDENTITY_CHANGE,NetworkTimelineSource.USER_CONFIRMED,NetworkTimelineSeverity.WARNING,20_000L,"Identité modifiée")
        ),30_000L,60_000L)
        assertEquals(2,r.acceptedEvents.size); assertEquals(0,r.rejectedEvents)
    }
    @Test fun oldEventsArePrunedByRetentionWindow(){
        val r=NetworkChangeTimeline.build(listOf(
            NetworkTimelineEvent(a,NetworkTimelineEventKind.FIRST_SEEN,NetworkTimelineSource.ANDROID_WIFI,NetworkTimelineSeverity.INFO,1_000L,"Ancienne observation"),
            NetworkTimelineEvent(a,NetworkTimelineEventKind.RETURNED,NetworkTimelineSource.ANDROID_WIFI,NetworkTimelineSeverity.INFO,95_000L,"Retour récent")
        ),100_000L,10_000L)
        assertEquals(1,r.acceptedEvents.size); assertEquals(1,r.prunedEvents)
    }
    @Test fun malformedEventsAreRejected(){
        val r=NetworkChangeTimeline.build(listOf(
            NetworkTimelineEvent("AA:BB:CC:DD:EE:FF",NetworkTimelineEventKind.FIRST_SEEN,NetworkTimelineSource.ANDROID_WIFI,NetworkTimelineSeverity.INFO,1_000L,"MAC brute"),
            NetworkTimelineEvent(a,NetworkTimelineEventKind.FIRST_SEEN,NetworkTimelineSource.ANDROID_WIFI,NetworkTimelineSeverity.INFO,50_000L,"Future"),
            NetworkTimelineEvent(a,NetworkTimelineEventKind.FIRST_SEEN,NetworkTimelineSource.ANDROID_WIFI,NetworkTimelineSeverity.INFO,1_000L,"   ")
        ),10_000L,20_000L)
        assertTrue(r.acceptedEvents.isEmpty()); assertEquals(3,r.rejectedEvents)
    }
    @Test fun summaryIsNormalizedAndBounded(){
        val r=NetworkChangeTimeline.build(listOf(NetworkTimelineEvent(a,NetworkTimelineEventKind.RISK_SCORE_CHANGED,NetworkTimelineSource.RISK_DOSSIER,NetworkTimelineSeverity.WARNING,1_000L,"  Risque   augmenté   "+"x".repeat(300))),2_000L,10_000L)
        val s=r.acceptedEvents.single().summary; assertTrue(s.startsWith("Risque augmenté")); assertEquals(160,s.length)
    }
}
