package com.sentinel.quantum.security

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Read-only Android pairing inventory, independent of BLE advertisements.
 * A bonded device is NOT proof of a live link or an authenticated Sentinel channel.
 */
data class PairedBluetoothDevice(
    val displayName: String,
    val deviceType: String
)

enum class PairedBluetoothStatus { READY, PERMISSION_REQUIRED, BLUETOOTH_UNAVAILABLE, BLUETOOTH_DISABLED, ACCESS_DENIED }

data class PairedBluetoothResult(val status: PairedBluetoothStatus, val devices: List<PairedBluetoothDevice> = emptyList())

object PairedBluetoothInventory {
    /** Heuristic label only; this does not prove the device type. */
    fun classifyDisplayName(name: String): String {
        val normalized = name.trim().lowercase(java.util.Locale.ROOT)
        return if (listOf("watch", "montre", "galaxy fit", "gear s", "gear sport").any { normalized.contains(it) }) {
            "Montre probable"
        } else "Bluetooth"
    }

    @Suppress("DEPRECATION")
    fun read(context: Context): List<PairedBluetoothDevice> = inspect(context).devices

    @Suppress("DEPRECATION")
    fun inspect(context: Context): PairedBluetoothResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return PairedBluetoothResult(PairedBluetoothStatus.PERMISSION_REQUIRED)
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return PairedBluetoothResult(PairedBluetoothStatus.BLUETOOTH_UNAVAILABLE)
        return try {
            if (!adapter.isEnabled) return PairedBluetoothResult(PairedBluetoothStatus.BLUETOOTH_DISABLED)
            val devices = adapter.bondedDevices.orEmpty().map { device ->
                val name = device.name?.trim().orEmpty().ifBlank { "Appareil associé (nom indisponible)" }
                val type = classifyDisplayName(name)
                PairedBluetoothDevice(name, type)
            }.sortedBy { it.displayName }
            PairedBluetoothResult(PairedBluetoothStatus.READY, devices)
        } catch (_: SecurityException) {
            PairedBluetoothResult(PairedBluetoothStatus.ACCESS_DENIED)
        }
    }
}
