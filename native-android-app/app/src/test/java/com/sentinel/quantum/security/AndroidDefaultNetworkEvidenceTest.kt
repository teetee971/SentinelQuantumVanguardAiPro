package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidDefaultNetworkEvidenceTest {
    @Test fun classifiesEachTransport() {
        assertEquals(NetworkEvidenceSource.ANDROID_WIFI,
            AndroidDefaultNetworkEvidence.classifyTransport(false, true, false, false))
        assertEquals(NetworkEvidenceSource.ANDROID_CELLULAR,
            AndroidDefaultNetworkEvidence.classifyTransport(false, false, true, false))
        assertEquals(NetworkEvidenceSource.ANDROID_ETHERNET,
            AndroidDefaultNetworkEvidence.classifyTransport(false, false, false, true))
        assertEquals(NetworkEvidenceSource.ANDROID_OTHER,
            AndroidDefaultNetworkEvidence.classifyTransport(false, false, false, false))
    }

    @Test fun vpnTakesPrecedenceOverUnderlyingNetwork() {
        assertEquals(NetworkEvidenceSource.VPN,
            AndroidDefaultNetworkEvidence.classifyTransport(true, true, true, false))
    }
}
