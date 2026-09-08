package com.sentinel.quantum.security

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

data class DiscoveredBluetoothDevice(
    val name: String,
    val address: String,
    val rssiDbm: Int,
    val kind: BluetoothDeviceKind,
    val randomizedAddress: Boolean,
    val assessment: BluetoothRiskAssessment
)

/**
 * Scanner Bluetooth LE passif et local.
 *
 * Aucune connexion ni appairage n'est initié : seules les annonces publiques diffusées
 * par les appareils environnants sont observées, puis évaluées localement.
 */
class BluetoothScanner(context: Context) {

    private val appContext = context.applicationContext
    private val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val handler = Handler(Looper.getMainLooper())
    private val discovered = linkedMapOf<String, DiscoveredBluetoothDevice>()
    private var callback: ScanCallback? = null

    /** Autorisations à demander à l'utilisateur avant un scan. */
    val requiredPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Android 12 impose de demander la localisation approximative avec la localisation
            // précise, seule cette dernière autorisant le scan BLE.
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    private val mandatoryPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(): Boolean = mandatoryPermissions.all { permission ->
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * Lance un scan borné dans le temps. [onResults] est appelé à chaque mise à jour puis
     * une dernière fois à l'arrêt automatique du scan.
     */
    @SuppressLint("MissingPermission")
    fun scan(
        durationMs: Long = DEFAULT_SCAN_DURATION_MS,
        onResults: (List<DiscoveredBluetoothDevice>) -> Unit,
        onError: (String) -> Unit,
        onScanFinished: () -> Unit = {}
    ) {
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || scanner == null || !isBluetoothEnabled()) {
            onError("Bluetooth indisponible ou désactivé.")
            return
        }
        if (!hasPermissions()) {
            onError("Autorisation requise pour observer les appareils Bluetooth environnants.")
            return
        }

        stop()
        discovered.clear()
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                discovered[device.address] = toDevice(device, result.rssi)
                onResults(sortedResults())
            }

            override fun onScanFailed(errorCode: Int) {
                stop()
                onError("Scan Bluetooth impossible (code $errorCode).")
                onScanFinished()
            }
        }
        callback = scanCallback

        try {
            scanner.startScan(scanCallback)
        } catch (_: SecurityException) {
            callback = null
            onError("Autorisation Bluetooth refusée par le système.")
            return
        } catch (_: IllegalStateException) {
            callback = null
            onError("Bluetooth indisponible ou désactivé.")
            return
        }

        handler.postDelayed({
            stop()
            onResults(sortedResults())
            onScanFinished()
        }, durationMs)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val scanCallback = callback ?: return
        callback = null
        handler.removeCallbacksAndMessages(null)
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    // Les autorisations sont vérifiées avant le scan ; les accès restent protégés par runCatching.
    @SuppressLint("MissingPermission")
    private fun toDevice(device: BluetoothDevice, rssiDbm: Int): DiscoveredBluetoothDevice {
        val name = runCatching { device.name }.getOrNull()?.trim().orEmpty()
        val kind = runCatching { deviceKind(device.bluetoothClass) }.getOrDefault(BluetoothDeviceKind.UNKNOWN)
        val bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
        val randomizedAddress = runCatching { device.type == BluetoothDevice.DEVICE_TYPE_LE }.getOrDefault(false)

        return DiscoveredBluetoothDevice(
            name = name.ifEmpty { UNKNOWN_DEVICE_NAME },
            address = device.address.orEmpty(),
            rssiDbm = rssiDbm,
            kind = kind,
            randomizedAddress = randomizedAddress,
            assessment = BluetoothRiskEvaluator.evaluate(
                deviceName = name.takeIf { it.isNotEmpty() },
                kind = kind,
                bonded = bonded
            )
        )
    }

    private fun deviceKind(bluetoothClass: BluetoothClass?): BluetoothDeviceKind =
        when (bluetoothClass?.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> BluetoothDeviceKind.AUDIO
            BluetoothClass.Device.Major.PHONE -> BluetoothDeviceKind.PHONE
            BluetoothClass.Device.Major.COMPUTER -> BluetoothDeviceKind.COMPUTER
            BluetoothClass.Device.Major.WEARABLE -> BluetoothDeviceKind.WEARABLE
            else -> BluetoothDeviceKind.UNKNOWN
        }

    private fun sortedResults(): List<DiscoveredBluetoothDevice> =
        discovered.values.sortedWith(
            compareBy<DiscoveredBluetoothDevice> { riskOrder(it.assessment.riskLevel) }
                .thenByDescending { it.rssiDbm }
        )

    private fun riskOrder(level: NetworkRiskLevel): Int = when (level) {
        NetworkRiskLevel.HIGH -> 0
        NetworkRiskLevel.MEDIUM -> 1
        NetworkRiskLevel.LOW -> 2
    }

    companion object {
        const val UNKNOWN_DEVICE_NAME = "Appareil inconnu"
        const val DEFAULT_SCAN_DURATION_MS = 10_000L
    }
}
