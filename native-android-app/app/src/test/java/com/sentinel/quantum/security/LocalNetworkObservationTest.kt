package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkObservationTest {
    private fun observation(
        source: NetworkEvidenceSource = NetworkEvidenceSource.ANDROID_WIFI,
        confidence: NetworkEvidenceConfidence = NetworkEvidenceConfidence.OBSERVED,
        observed: Long = 1000L,
        expires: Long = 2000L,
    ) = LocalNetworkObservation(
        id = "local-test-id",
        source = source,
        observedAtMillis = observed,
        expiresAtMillis = expires,
        confidence = confidence,
        sourceVersion = "test-1",
    )

    @Test fun expiresAtBoundary() {
        val item = observation()
        assertFalse(item.isFresh(999L))
        assertTrue(item.isFresh(1000L))
        assertTrue(item.isFresh(1999L))
        assertFalse(item.isFresh(2000L))
    }

    @Test fun rejectsInvalidTimeWindows() {
        assertFalse(observation(observed = -1L).isFresh(1000L))
        assertFalse(observation(expires = 1000L).isFresh(1000L))
        assertFalse(observation(expires = 999L).isFresh(1000L))
    }

    @Test fun userReportAndInferencesDoNotCountAsDirectEvidence() {
        assertFalse(observation(source = NetworkEvidenceSource.USER_REPORT).isDirectEvidence(1500L))
        assertFalse(observation(confidence = NetworkEvidenceConfidence.INFERRED).isDirectEvidence(1500L))
        assertFalse(observation(confidence = NetworkEvidenceConfidence.UNKNOWN).isDirectEvidence(1500L))
        assertTrue(observation().isDirectEvidence(1500L))
        assertFalse(observation().isDirectEvidence(2500L))
    }

    @Test fun onlyFreshEvidenceSurvivesAndSortsByRecency() {
        val old = observation(observed = 500L, expires = 1300L).copy(id = "old")
        val newer = observation(observed = 1200L, expires = 2000L).copy(id = "new")
        val stale = observation(observed = 100L, expires = 400L).copy(id = "stale")
        assertEquals(listOf("new", "old"), NetworkObservationPolicy.freshEvidence(listOf(old, stale, newer), 1250L).map { it.id })
    }
    @Test fun excludesObservationsWithoutProvenance() {
        val noId = observation().copy(id = "")
        val noVersion = observation().copy(id = "known", sourceVersion = "  ")
        assertFalse(noId.isDirectEvidence(1500L))
        assertFalse(noVersion.isDirectEvidence(1500L))
        assertTrue(NetworkObservationPolicy.freshEvidence(listOf(noId, noVersion), 1500L).isEmpty())
    }
    @Test fun duplicateCollapseIsScopedToSourceAndId() {
        val early = observation(observed = 1000L, expires = 2500L).copy(id = "shared")
        val late = observation(observed = 1200L, expires = 2500L).copy(id = "shared")
        val otherSource = late.copy(source = NetworkEvidenceSource.ANDROID_BLE)
        val distinct = late.copy(id = "other")
        val output = NetworkObservationPolicy.latestPerSourceId(listOf(early, otherSource, late, distinct), 1500L)
        assertEquals(3, output.size)
        assertEquals(1, output.count { it.id == "shared" && it.source == NetworkEvidenceSource.ANDROID_WIFI })
        assertTrue(output.any { it.id == "shared" && it.source == NetworkEvidenceSource.ANDROID_BLE })
        assertTrue(output.any { it.id == "shared" && it.observedAtMillis == 1200L && it.source == NetworkEvidenceSource.ANDROID_WIFI })
    }
}
