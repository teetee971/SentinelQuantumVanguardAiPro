package com.sentinel.quantum.security

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Opt-in, discovery-only BLE experiment for Offline Mesh.
 * Does not expose GATT characteristics or transfer payloads; never marks a peer authenticated.
 * All public calls must originate on the main thread. No background or automatic startup.
 */
class OfflineMeshBleDiscovery(context: Context) {
    enum class StartResult { STARTED, BUSY, PERMISSION_REQUIRED, UNAVAILABLE, BLUETOOTH_DISABLED, ADVERTISE_UNSUPPORTED, WRONG_THREAD, START_FAILED }
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val manager = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val serviceUuid = ParcelUuid(UUID.fromString("de939bda-5b41-4c5d-8bb7-58c24458a6e2"))
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var advertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var expiry: Runnable? = null
    private var generation: Long = 0
    private var active: Boolean = false

    private fun onMainThread() = Looper.myLooper() == Looper.getMainLooper()
    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    private fun permissionsGranted(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        granted(Manifest.permission.BLUETOOTH_SCAN) &&
            granted(Manifest.permission.BLUETOOTH_CONNECT) &&
            granted(Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        granted(Manifest.permission.BLUETOOTH) &&
            granted(Manifest.permission.BLUETOOTH_ADMIN) &&
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** The app must separately request permissions before calling start(). */
    @SuppressLint("MissingPermission")
    fun start(
        durationMs: Long = 12_000L,
        onSignal: (rssiDbm: Int) -> Unit = {},
        onStopped: (String) -> Unit = {}
    ): StartResult {
        if (!onMainThread()) return StartResult.WRONG_THREAD
        if (active) return StartResult.BUSY
        if (durationMs !in 1_000L..30_000L) return StartResult.START_FAILED
        if (!permissionsGranted()) return StartResult.PERMISSION_REQUIRED
        val adapter = manager?.adapter ?: return StartResult.UNAVAILABLE
        if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return StartResult.UNAVAILABLE
        if (!adapter.isEnabled) return StartResult.BLUETOOTH_DISABLED
        val nextScanner = adapter.bluetoothLeScanner ?: return StartResult.UNAVAILABLE
        val nextAdvertiser = adapter.bluetoothLeAdvertiser ?: return StartResult.ADVERTISE_UNSUPPORTED
        generation++
        val session = generation
        active = true
        scanner = nextScanner
        advertiser = nextAdvertiser
        val scan = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (active && generation == session) onSignal(result.rssi)
            }
            override fun onScanFailed(errorCode: Int) {
                if (active && generation == session) stopInternal("SCAN_FAILED", onStopped)
            }
        }
        val advert = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                if (active && generation == session) stopInternal("ADVERTISE_FAILED", onStopped)
            }
        }
        scanCallback = scan
        advertiseCallback = advert
        return try {
            val data = AdvertiseData.Builder().addServiceUuid(serviceUuid).setIncludeDeviceName(false).build()
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(false)
                .setTimeout(durationMs.toInt())
                .build()
            nextAdvertiser.startAdvertising(settings, data, advert)
            val filter = ScanFilter.Builder().setServiceUuid(serviceUuid).build()
            val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
            nextScanner.startScan(listOf(filter), scanSettings, scan)
            val timeout = Runnable {
                if (active && generation == session) stopInternal("TIMEOUT", onStopped)
            }
            expiry = timeout
            handler.postDelayed(timeout, durationMs)
            StartResult.STARTED
        } catch (_: SecurityException) {
            stopInternal("PERMISSION_REVOKED", onStopped)
            StartResult.PERMISSION_REQUIRED
        } catch (_: RuntimeException) {
            stopInternal("START_FAILED", onStopped)
            StartResult.START_FAILED
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(): Boolean {
        if (!onMainThread()) return false
        if (!active) return true
        stopInternal("USER_STOPPED", {})
        return true
    }

    @SuppressLint("MissingPermission")
    private fun stopInternal(reason: String, onStopped: (String) -> Unit) {
        generation++
        active = false
        expiry?.let(handler::removeCallbacks)
        expiry = null
        val oldScanner = scanner
        val oldScan = scanCallback
        val oldAdvertiser = advertiser
        val oldAdvert = advertiseCallback
        scanner = null; scanCallback = null; advertiser = null; advertiseCallback = null
        try { if (oldScan != null) oldScanner?.stopScan(oldScan) } catch (_: RuntimeException) { }
        try { if (oldAdvert != null) oldAdvertiser?.stopAdvertising(oldAdvert) } catch (_: RuntimeException) { }
        onStopped(reason)
    }
}
