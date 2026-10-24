package com.sentinel.quantum.security

/**
 * Offline Mesh capability decision. No radio access or permission request happens here.
 * READY means prerequisites for attempting a BLE session, not an established connection.
 */
object OfflineMeshBleReadiness {
    enum class Status { READY, LIMITED, LOCKED }
    data class Result(val status: Status, val reason: String)
    data class Inputs(
        val apiLevel: Int,
        val bluetoothLePresent: Boolean,
        val adapterPresent: Boolean,
        val adapterEnabled: Boolean,
        val advertiserSupported: Boolean,
        val scanGranted: Boolean,
        val connectGranted: Boolean,
        val advertiseGranted: Boolean,
        val legacyBluetoothGranted: Boolean = false,
        val legacyAdminGranted: Boolean = false,
        val locationGranted: Boolean = false
    )

    fun evaluate(i: Inputs): Result {
        if (!i.bluetoothLePresent || !i.adapterPresent) return Result(Status.LOCKED, "BLE_UNAVAILABLE")
        if (i.apiLevel >= 31) {
            if (!i.scanGranted || !i.connectGranted || !i.advertiseGranted)
                return Result(Status.LOCKED, "BLE_PERMISSION_REQUIRED")
        } else {
            if (!i.legacyBluetoothGranted || !i.legacyAdminGranted || !i.locationGranted)
                return Result(Status.LOCKED, "BLE_LEGACY_PERMISSION_REQUIRED")
        }
        if (!i.adapterEnabled) return Result(Status.LIMITED, "BLUETOOTH_DISABLED")
        if (!i.advertiserSupported) return Result(Status.LIMITED, "BLE_ADVERTISING_UNSUPPORTED")
        return Result(Status.READY, "BLE_PREREQUISITES_AVAILABLE")
    }
}
