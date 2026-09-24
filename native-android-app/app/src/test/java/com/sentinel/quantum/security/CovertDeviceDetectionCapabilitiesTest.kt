package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CovertDeviceDetectionCapabilitiesTest {

    @Test
    fun bluetoothTrackerDetectionIsLocalAndActive() {
        assertEquals(
            CovertDetectionSupport.ACTIVE_LOCAL,
            CovertDeviceDetectionCapabilities.supportFor(CovertDetectionChannel.BLUETOOTH_TRACKER)
        )
    }

    @Test
    fun wirelessCameraDetectionIsExplicitlyHeuristic() {
        assertEquals(
            CovertDetectionSupport.ACTIVE_HEURISTIC,
            CovertDeviceDetectionCapabilities.supportFor(CovertDetectionChannel.WIRELESS_CAMERA_HINT)
        )
    }

    @Test
    fun opticalSweepNeverPretendsToBeActiveWithoutCameraModule() {
        assertEquals(
            CovertDetectionSupport.USER_CAMERA_REQUIRED,
            CovertDeviceDetectionCapabilities.supportFor(CovertDetectionChannel.OPTICAL_LENS_SWEEP)
        )
    }

    @Test
    fun rfSpectrumRequiresDedicatedExternalHardware() {
        assertEquals(
            CovertDetectionSupport.EXTERNAL_RF_HARDWARE_REQUIRED,
            CovertDeviceDetectionCapabilities.supportFor(CovertDetectionChannel.RF_SPECTRUM)
        )
    }

    @Test
    fun capabilityMapCoversAllDeclaredChannels() {
        assertEquals(CovertDetectionChannel.entries.size, CovertDeviceDetectionCapabilities.channels.size)
        assertTrue(CovertDeviceDetectionCapabilities.channels.map { it.channel }.containsAll(CovertDetectionChannel.entries))
    }
}
