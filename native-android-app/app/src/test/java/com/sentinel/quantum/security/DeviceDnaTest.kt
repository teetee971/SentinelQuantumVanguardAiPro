package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDnaTest {

    @Test
    fun aggregatesDistinctLocalEvidenceWithoutClaimingIdentityProof() {
        val profile = DeviceDnaBuilder.build(
            preferredLabel = "Salon TV",
            evidence = listOf(
                DeviceIdentityEvidence(DeviceEvidenceSource.WIFI_BSSID, "AA:BB:CC:DD:EE:FF", 25),
                DeviceIdentityEvidence(DeviceEvidenceSource.WIFI_SSID, "Maison", 10),
                DeviceIdentityEvidence(DeviceEvidenceSource.WIFI_BSSID, "aa:bb:cc:dd:ee:ff", 25)
            ),
            trustedByUser = false
        )

        assertEquals("Salon TV", profile.label)
        assertEquals(35, profile.confidencePercent)
        assertEquals(2, profile.evidenceCount)
        assertTrue(DeviceEvidenceSource.WIFI_BSSID in profile.sources)
        assertFalse(profile.trustedByUser)
    }

    @Test
    fun userTrustRaisesConfidenceButDoesNotExceedOneHundred() {
        val profile = DeviceDnaBuilder.build(
            preferredLabel = null,
            evidence = DeviceEvidenceSource.entries.map {
                DeviceIdentityEvidence(it, it.name, 30)
            },
            trustedByUser = true
        )

        assertEquals("Appareil inconnu", profile.label)
        assertEquals(100, profile.confidencePercent)
        assertTrue(profile.trustedByUser)
    }

    @Test
    fun emptyEvidenceIsIgnoredAndLongLabelsAreBounded() {
        val profile = DeviceDnaBuilder.build(
            preferredLabel = "x".repeat(200),
            evidence = listOf(DeviceIdentityEvidence(DeviceEvidenceSource.BLE_NAME, "   ", 30)),
            trustedByUser = false
        )

        assertEquals(96, profile.label.length)
        assertEquals(0, profile.evidenceCount)
        assertEquals(0, profile.confidencePercent)
    }
}
