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

    /** Autorisations réellement nécessaires au produit pour exécuter ce scan et lire ses métadonnées. */
    val requiredPermissions: Array<String> = requiredPermissionsForApi(Build.VERSION.SDK_INT)

    /**
     * Source unique de vérité pour l'UI et le scanner.
     *
     * Android 12+ sépare le scan BLE de l'accès aux métadonnées de l'appareil. Sentinel utilise
     * les deux pour nommer/classer les appareils et évaluer le risque : SCAN seul n'est donc pas un
     * état "autorisé" complet.
     */
    fun hasPermissions(): Boolean = requiredPermissions.all { permission ->
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Indique uniquement si l'adaptateur est activé dans un contexte où Sentinel détient déjà
     * les autorisations Bluetooth requises. Un résultat false ne doit jamais être interprété comme
     * "Bluetooth désactivé" sans vérifier séparément [hasPermissions].
     */
    @SuppressLint("MissingPermission")
    fun isBluetoothEnabledWhenAuthorized(): Boolean {
        val currentAdapter = adapter ?: return false
        if (!hasPermissions()) return false
        return runCatching { currentAdapter.isEnabled }.getOrDefault(false)
    }

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
        val currentAdapter = adapter
        if (currentAdapter == null) {
            onError("Bluetooth indisponible sur cet appareil.")
            return
        }
        // Permission truth must be established before touching protected adapter/scanner APIs.
        if (!hasPermissions()) {
            onError("Autorisations Bluetooth requises incomplètes.")
            return
        }

        val scanner = try {
            if (!currentAdapter.isEnabled) null else currentAdapter.bluetoothLeScanner
        } catch (_: SecurityException) {
            onError("Autorisation Bluetooth refusée ou révoquée par le système.")
            return
        }
        if (scanner == null) {
            onError("Bluetooth indisponible ou désactivé.")
            return
        }

        stop()
        discovered.clear()
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (callback !== this) return
                val scanResult = result ?: return
                val device = scanResult.device ?: return
                val mapped = toDevice(device, scanResult)
                val sessionKey = sessionKeyForAddress(mapped.address)
                if (sessionKey == null) {
                    // BLUETOOTH_CONNECT can be revoked while the scan is active. Without a stable,
                    // authorized address Sentinel cannot deduplicate truthfully, so stop instead of
                    // manufacturing an identity from the in-memory BluetoothDevice instance.
                    stop()
                    onError("Métadonnées Bluetooth devenues indisponibles ; autorisation à vérifier.")
                    onScanFinished()
                    return
                }
                discovered[sessionKey] = mapped
                onResults(sortedResults())
            }

            override fun onScanFailed(errorCode: Int) {
                if (callback !== this) return
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
            onError("Autorisation Bluetooth refusée ou révoquée par le système.")
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

    // Les autorisations sont vérifiées avant le scan ; une révocation concurrente reste tolérée
    // en protégeant chaque lecture de métadonnée individuellement.
    @SuppressLint("MissingPermission")
    private fun toDevice(device: BluetoothDevice, result: ScanResult): DiscoveredBluetoothDevice {
        val systemName = runCatching { device.name }.getOrNull()?.trim().orEmpty()
        val advertisedName = result.scanRecord?.deviceName?.trim().orEmpty()
        val name = systemName.ifEmpty { advertisedName }
        val kind = runCatching { deviceKind(device.bluetoothClass) }.getOrDefault(BluetoothDeviceKind.UNKNOWN)
        val bonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
        val randomizedAddress = runCatching { device.type == BluetoothDevice.DEVICE_TYPE_LE }.getOrDefault(false)
        val address = runCatching { device.address }.getOrNull()?.trim().orEmpty()

        return DiscoveredBluetoothDevice(
            name = name.ifEmpty { UNKNOWN_DEVICE_NAME },
            address = address,
            rssiDbm = result.rssi,
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

        internal fun requiredPermissionsForApi(sdkInt: Int): Array<String> =
            if (sdkInt >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            }

        internal fun sessionKeyForAddress(address: String): String? =
            address.trim().takeIf { it.isNotEmpty() }
    }
}
