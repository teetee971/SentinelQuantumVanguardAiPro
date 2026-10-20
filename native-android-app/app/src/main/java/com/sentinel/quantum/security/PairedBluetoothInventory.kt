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

object PairedBluetoothInventory {
    @Suppress("DEPRECATION")
    fun read(context: Context): List<PairedBluetoothDevice> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty().map { device ->
                val name = device.name?.trim().orEmpty().ifBlank { "Appareil associé (nom indisponible)" }
                val type = if (name.contains("watch", ignoreCase = true) ||
                    name.contains("montre", ignoreCase = true)) "Montre probable" else "Bluetooth"
                PairedBluetoothDevice(name, type)
            }.sortedBy { it.displayName }
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
