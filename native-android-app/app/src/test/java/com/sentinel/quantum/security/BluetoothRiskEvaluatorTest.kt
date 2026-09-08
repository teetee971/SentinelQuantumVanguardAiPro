package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothRiskEvaluatorTest {

    @Test
    fun detectsCommonTrackerNames() {
        assertTrue(BluetoothRiskEvaluator.isLikelyTracker("AirTag"))
        assertTrue(BluetoothRiskEvaluator.isLikelyTracker("Tile Pro"))
        assertTrue(BluetoothRiskEvaluator.isLikelyTracker("Galaxy SmartTag2"))
        assertTrue(BluetoothRiskEvaluator.isLikelyTracker("chipolo one"))
        assertTrue(BluetoothRiskEvaluator.isLikelyTracker("Réseau Find My"))
    }

    @Test
    fun doesNotFlagOrdinaryDeviceNames() {
        assertFalse(BluetoothRiskEvaluator.isLikelyTracker("Casque Bureau"))
        assertFalse(BluetoothRiskEvaluator.isLikelyTracker(null))
        assertFalse(BluetoothRiskEvaluator.isLikelyTracker("   "))
    }

    @Test
    fun trackerIsHighRisk() {
        val assessment = BluetoothRiskEvaluator.evaluate(
            deviceName = "AirTag",
            kind = BluetoothDeviceKind.UNKNOWN
        )

        assertEquals(NetworkRiskLevel.HIGH, assessment.riskLevel)
        assertTrue(assessment.likelyTracker)
        assertTrue(assessment.reasons.contains(BluetoothRiskEvaluator.TRACKER_ADVICE))
    }

    @Test
    fun unknownDeviceIsMediumRisk() {
        val unnamed = BluetoothRiskEvaluator.evaluate(deviceName = null)
        val unknownKind = BluetoothRiskEvaluator.evaluate(
            deviceName = "XZ-4410",
            kind = BluetoothDeviceKind.UNKNOWN,
            bonded = true
        )

        assertEquals(NetworkRiskLevel.MEDIUM, unnamed.riskLevel)
        assertEquals(NetworkRiskLevel.MEDIUM, unknownKind.riskLevel)
        assertFalse(unknownKind.likelyTracker)
    }

    @Test
    fun knownPhonesAndAudioDevicesAreLowRisk() {
        val audio = BluetoothRiskEvaluator.evaluate(
            deviceName = "Casque Bureau",
            kind = BluetoothDeviceKind.AUDIO
        )
        val phone = BluetoothRiskEvaluator.evaluate(
            deviceName = "Téléphone Durand",
            kind = BluetoothDeviceKind.PHONE,
            bonded = true
        )

        assertEquals(NetworkRiskLevel.LOW, audio.riskLevel)
        assertEquals(NetworkRiskLevel.LOW, phone.riskLevel)
    }

    @Test
    fun trackerRemainsHighRiskEvenForKnownDeviceKind() {
        val assessment = BluetoothRiskEvaluator.evaluate(
            deviceName = "Tile Mate",
            kind = BluetoothDeviceKind.AUDIO,
            bonded = true
        )

        assertEquals(NetworkRiskLevel.HIGH, assessment.riskLevel)
    }
}
