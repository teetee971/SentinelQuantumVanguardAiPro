package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEvidenceTransportTest {
    @Test fun transportSourcesRemainSeparate() {
        val sources = listOf(
            NetworkEvidenceSource.ANDROID_WIFI,
            NetworkEvidenceSource.ANDROID_CELLULAR,
            NetworkEvidenceSource.ANDROID_ETHERNET,
            NetworkEvidenceSource.VPN,
            NetworkEvidenceSource.ANDROID_OTHER
        )
        assertEquals(sources.size, sources.toSet().size)
    }

    @Test fun passiveTransportObservationDoesNotImplyDeviceIdentity() {
        val observation = LocalNetworkObservation(
            id = "test-observation",
            source = NetworkEvidenceSource.ANDROID_CELLULAR,
            observedAtMillis = 1000L,
            expiresAtMillis = 31000L,
            confidence = NetworkEvidenceConfidence.OBSERVED,
            sourceVersion = "android-default-transport-v1:cellular"
        )
        assertTrue(observation.isDirectEvidence(1500L))
        assertEquals(NetworkEvidenceSource.ANDROID_CELLULAR, observation.source)
    }
}
