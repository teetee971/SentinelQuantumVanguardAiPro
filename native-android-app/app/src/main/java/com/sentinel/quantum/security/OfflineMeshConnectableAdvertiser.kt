package com.sentinel.quantum.security

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * User-initiated, bounded connectable BLE advertising for the discovery-only
 * Sentinel GATT marker service. Start only after GATT service registration.
 * This component neither confirms peer authentication nor transmits messages.
 * All public methods must run on the Android main thread.
 */
class OfflineMeshConnectableAdvertiser(context: Context) {
    enum class Start { STARTING, BUSY, PERMISSION_REQUIRED, BLUETOOTH_DISABLED, UNAVAILABLE, WRONG_THREAD, FAILED }
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val manager = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val service = ParcelUuid(UUID.fromString("de939bda-5b41-4c5d-8bb7-58c24458a6e2"))
    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null
    private var watchdog: Runnable? = null
    private var session = 0L
    private var onStopped: ((String) -> Unit)? = null

    private fun permitted(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            (ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_ADVERTISE) ==
                PackageManager.PERMISSION_GRANTED &&
             ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED)

    @SuppressLint("MissingPermission")
    fun start(durationMs: Long = 12_000L, stopped: (String) -> Unit = {}): Start {
        if (Looper.myLooper() != Looper.getMainLooper()) return Start.WRONG_THREAD
        if (callback != null) return Start.BUSY
        if (durationMs !in 1_000L..30_000L) return Start.FAILED
        if (!permitted()) return Start.PERMISSION_REQUIRED
        val adapter = manager?.adapter ?: return Start.UNAVAILABLE
        if (!adapter.isEnabled) return Start.BLUETOOTH_DISABLED
        val radio = adapter.bluetoothLeAdvertiser ?: return Start.UNAVAILABLE
        val current = ++session
        val cb = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                main.post {
                    if (current == session) stopInternal("START_FAILED")
                }
            }
        }
        advertiser = radio
        callback = cb
        onStopped = stopped
        return try {
            val data = AdvertiseData.Builder()
                .addServiceUuid(service)
                .setIncludeDeviceName(false)
                .build()
            val settings = AdvertiseSettings.Builder()
                .setConnectable(true)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setTimeout(durationMs.toInt())
                .build()
            radio.startAdvertising(settings, data, cb)
            val task = Runnable {
                if (current == session) stopInternal("TIMEOUT")
            }
            watchdog = task
            main.postDelayed(task, durationMs)
            Start.STARTING
        } catch (_: SecurityException) {
            stopInternal("PERMISSION_REVOKED")
            Start.PERMISSION_REQUIRED
        } catch (_: RuntimeException) {
            stopInternal("START_FAILED")
            Start.FAILED
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) return false
        stopInternal("USER_STOPPED")
        return true
    }

    @SuppressLint("MissingPermission")
    private fun stopInternal(reason: String) {
        if (callback == null) return
        session++
        watchdog?.let(main::removeCallbacks)
        watchdog = null
        val cb = callback
        val radio = advertiser
        callback = null
        advertiser = null
        try { if (cb != null) radio?.stopAdvertising(cb) } catch (_: RuntimeException) {}
        val notify = onStopped
        onStopped = null
        notify?.invoke(reason)
    }
}
