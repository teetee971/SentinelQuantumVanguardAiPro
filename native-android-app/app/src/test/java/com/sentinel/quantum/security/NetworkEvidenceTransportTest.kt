package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEvidenceTransportTest {
    @Test fun transportSourcesRemainSeparate() {
        val sources = listOf(
            LocalObservationSource.ANDROID_WIFI,
            LocalObservationSource.ANDROID_CELLULAR,
            LocalObservationSource.ANDROID_ETHERNET,
            LocalObservationSource.VPN,
            LocalObservationSource.ANDROID_OTHER
        )
        assertEquals(sources.size, sources.toSet().size)
    }

    @Test fun passiveTransportObservationDoesNotImplyDeviceIdentity() {
        val observation = LocalNetworkObservation(
            id = "test-observation",
            source = LocalObservationSource.ANDROID_CELLULAR,
            observedAtMillis = 1000L,
            expiresAtMillis = 31000L,
            confidence = NetworkEvidenceConfidence.OBSERVED,
            sourceVersion = "android-default-transport-v1:cellular"
        )
        assertTrue(observation.isDirectEvidence(1500L))
        assertEquals(LocalObservationSource.ANDROID_CELLULAR, observation.source)
    }
}
