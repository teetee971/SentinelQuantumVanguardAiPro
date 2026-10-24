package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineMeshBleReadinessTest {
    private fun modern() = OfflineMeshBleReadiness.Inputs(
        apiLevel=36, bluetoothLePresent=true, adapterPresent=true,
        adapterEnabled=true, advertiserSupported=true,
        scanGranted=true, connectGranted=true, advertiseGranted=true
    )

    @Test fun readyMeansOnlyPrerequisites() {
        assertEquals(OfflineMeshBleReadiness.Status.READY, OfflineMeshBleReadiness.evaluate(modern()).status)
    }

    @Test fun missingAdvertisePermissionFailsClosed() {
        assertEquals("BLE_PERMISSION_REQUIRED", OfflineMeshBleReadiness.evaluate(modern().copy(advertiseGranted=false)).reason)
    }

    @Test fun disabledAdapterIsNotReady() {
        assertEquals(OfflineMeshBleReadiness.Status.LIMITED, OfflineMeshBleReadiness.evaluate(modern().copy(adapterEnabled=false)).status)
    }

    @Test fun noAdvertisingHardwareIsNotReady() {
        assertEquals("BLE_ADVERTISING_UNSUPPORTED", OfflineMeshBleReadiness.evaluate(modern().copy(advertiserSupported=false)).reason)
    }

    @Test fun missingBleHardwareIsLocked() {
        assertEquals(OfflineMeshBleReadiness.Status.LOCKED, OfflineMeshBleReadiness.evaluate(modern().copy(bluetoothLePresent=false)).status)
    }

    @Test fun legacyAndroidUsesLegacyPermissions() {
        val legacy = modern().copy(apiLevel=30,scanGranted=false,connectGranted=false,
            advertiseGranted=false,legacyBluetoothGranted=true,legacyAdminGranted=true,locationGranted=true)
        assertEquals(OfflineMeshBleReadiness.Status.READY, OfflineMeshBleReadiness.evaluate(legacy).status)
        assertEquals("BLE_LEGACY_PERMISSION_REQUIRED",
            OfflineMeshBleReadiness.evaluate(legacy.copy(locationGranted=false)).reason)
    }
}
