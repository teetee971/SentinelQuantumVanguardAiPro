package com.sentinel.quantum.security

import android.Manifest
import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothScannerPermissionPolicyTest {
    @Test
    fun android12PlusRequiresScanAndConnect() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            BluetoothScanner.requiredPermissionsForApi(Build.VERSION_CODES.S)
        )
    }

    @Test
    fun modernApisKeepTheSameCompleteBluetoothPermissionContract() {
        assertArrayEquals(
            BluetoothScanner.requiredPermissionsForApi(Build.VERSION_CODES.S),
            BluetoothScanner.requiredPermissionsForApi(API_35)
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

    @Test
    fun sessionIdentityRequiresAnAuthorizedNonBlankBluetoothAddress() {
        assertNull(BluetoothScanner.sessionKeyForAddress(""))
        assertNull(BluetoothScanner.sessionKeyForAddress("   "))
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            BluetoothScanner.sessionKeyForAddress("  AA:BB:CC:DD:EE:FF  ")
        )
    }

    private companion object {
        const val API_35 = 35
    }
}
