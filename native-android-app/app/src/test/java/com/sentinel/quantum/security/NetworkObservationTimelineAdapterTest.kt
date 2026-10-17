package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkObservationTimelineAdapterTest {

    @Test
    fun newAndKnownFingerprintsBecomeBoundedTimelineEvents() {
        val newFingerprint = "a".repeat(64)
        val knownFingerprint = "b".repeat(64)

        val events = NetworkObservationTimelineAdapter.events(
            source = NetworkTimelineSource.ANDROID_BLE,
            fingerprints = listOf(newFingerprint, knownFingerprint),
            knownFingerprints = setOf(knownFingerprint),
            observedAtMs = 1_000L
        )

        assertEquals(2, events.size)
        assertEquals(NetworkTimelineEventKind.FIRST_SEEN, events.first { it.subjectFingerprint == newFingerprint }.kind)
        assertEquals(NetworkTimelineEventKind.RETURNED, events.first { it.subjectFingerprint == knownFingerprint }.kind)
        assertTrue(events.all { it.source == NetworkTimelineSource.ANDROID_BLE })
        assertTrue(events.all { it.summary.length <= 160 })
    }

    @Test
    fun invalidOrDuplicateFingerprintsAreNotPublished() {
        val valid = "c".repeat(64)

        val events = NetworkObservationTimelineAdapter.events(
            source = NetworkTimelineSource.ANDROID_WIFI,
            fingerprints = listOf(valid, valid, "raw-mac", "d".repeat(65)),
            knownFingerprints = emptySet(),
            observedAtMs = 2_000L
        )

        assertEquals(listOf(valid), events.map { it.subjectFingerprint })
    }
}
