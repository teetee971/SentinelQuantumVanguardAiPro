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
    fun detectsCameraRecorderAndBeaconNames() {
        assertTrue(BluetoothRiskEvaluator.isLikelyCameraOrRecorder("V380 Mini Camera"))
        assertTrue(BluetoothRiskEvaluator.isLikelyCameraOrRecorder("Voice Recorder"))
        assertTrue(BluetoothRiskEvaluator.isLikelyBeacon("iBeacon"))
        assertFalse(BluetoothRiskEvaluator.isLikelyCameraOrRecorder("Casque Bureau"))
    }

    @Test
    fun doesNotFlagOrdinaryDeviceNames() {
        assertFalse(BluetoothRiskEvaluator.isLikelyTracker("Casque Bureau"))
        assertFalse(BluetoothRiskEvaluator.isLikelyTracker(null))
        assertFalse(BluetoothRiskEvaluator.isLikelyTracker("   "))
    }

    @Test
    fun cameraAndBeaconAreMediumRiskSignals() {
        val camera = BluetoothRiskEvaluator.evaluate(deviceName = "WiFi Cam", kind = BluetoothDeviceKind.UNKNOWN)
        val beacon = BluetoothRiskEvaluator.evaluate(deviceName = "Estimote Beacon", kind = BluetoothDeviceKind.UNKNOWN)

        assertEquals(NetworkRiskLevel.MEDIUM, camera.riskLevel)
        assertTrue(camera.likelyCameraOrRecorder)
        assertEquals(NetworkRiskLevel.MEDIUM, beacon.riskLevel)
        assertTrue(beacon.likelyBeacon)
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

    @Test
    fun appleFindMyAnnouncementIsNotProofOfStalking() {
        val payload = ByteArray(27)
        payload[0] = 0x12
        payload[1] = 0x19
        assertTrue(BluetoothRiskEvaluator.isAppleFindMy(0x004C, payload))
        assertFalse(BluetoothRiskEvaluator.isAppleFindMy(0x004C, byteArrayOf(0x10, 0x05, 0, 0, 0, 0, 0)))
        val assessment = BluetoothRiskEvaluator.evaluate(deviceName = null, findMyAnnouncement = true)
        assertEquals(NetworkRiskLevel.MEDIUM, assessment.riskLevel)
        assertFalse(assessment.likelyTracker)
        assertTrue(assessment.announcesFindMy)
        val airtag = ByteArray(27)
        airtag[0] = 0x12
        airtag[1] = 0x19
        airtag[2] = 0x10
        val airpods = airtag.copyOf()
        airpods[2] = 0x18
        val phone = airtag.copyOf()
        phone[2] = 0x00
        assertTrue(BluetoothRiskEvaluator.appleFindMyLabel(airtag)!!.contains("AirTag"))
        assertTrue(BluetoothRiskEvaluator.appleFindMyLabel(airtag)!!.contains("pleine"))
        val low = airtag.copyOf()
        low[2] = 0x90.toByte()
        assertTrue(BluetoothRiskEvaluator.appleFindMyLabel(low)!!.contains("basse"))
        assertTrue(BluetoothRiskEvaluator.appleFindMyLabel(airpods)!!.contains("AirPods"))
        assertTrue(BluetoothRiskEvaluator.appleFindMyLabel(phone)!!.contains("sans le motif AirTag"))
        val tile = BluetoothRiskEvaluator.evaluate(
            deviceName = null,
            trackerNote = "Annonce Tile. Pas une preuve de suivi."
        )
        assertTrue(tile.announcesKnownTracker)
        assertFalse(tile.likelyTracker)
        assertEquals(NetworkRiskLevel.MEDIUM, tile.riskLevel)
    }
}
