package com.sentinel.quantum.security

import android.Manifest
import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothScannerPermissionPolicyTest {
    @Test
    fun android12PlusRequiresScanAndConnect() {
        val permissions = BluetoothScanner.requiredPermissionsForApi(Build.VERSION_CODES.S)

        assertArrayEquals(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            permissions
        )
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
    }

    @Test
    fun modernApisKeepTheSameCompleteBluetoothPermissionContract() {
        assertArrayEquals(
            BluetoothScanner.requiredPermissionsForApi(Build.VERSION_CODES.S),
            BluetoothScanner.requiredPermissionsForApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        )
    }

    @Test
    fun preAndroid12UsesLocationPermissionsForBleDiscovery() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            BluetoothScanner.requiredPermissionsForApi(Build.VERSION_CODES.R)
        )
    }
}
