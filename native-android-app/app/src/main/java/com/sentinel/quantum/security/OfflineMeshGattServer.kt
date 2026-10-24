package com.sentinel.quantum.security

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Experimental service-registration-only GATT server, intentionally no data path.
 * Not discoverable on its own: a separate connectable BLE advertisement is required.
 * No private payload is accepted or returned; no authenticated identity is claimed.
 */
class OfflineMeshGattServer(context: Context) {
    enum class Start { STARTING, BUSY, PERMISSION_REQUIRED, UNAVAILABLE, WRONG_THREAD, FAILED }
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val manager = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val serviceId = UUID.fromString("de939bda-5b41-4c5d-8bb7-58c24458a6e2")
    private var server: BluetoothGattServer? = null
    private var generation = 0L
    private var onReady: ((Boolean) -> Unit)? = null
    private var timeout: Runnable? = null

    private fun onMain() = Looper.myLooper() == Looper.getMainLooper()
    private fun permitted() = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(onRegistered: (Boolean) -> Unit = {}): Start {
        if (!onMain()) return Start.WRONG_THREAD
        if (server != null) return Start.BUSY
        if (!permitted()) return Start.PERMISSION_REQUIRED
        val radioManager = manager ?: return Start.UNAVAILABLE
        val run = ++generation
        val callback = object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                main.post {
                    if (run != generation || server == null) return@post
                    if (service.uuid != serviceId || status != BluetoothGatt.GATT_SUCCESS) {
                        stopInternal(false)
                    } else {
                        timeout?.let(main::removeCallbacks)
                        timeout = null
                        val notify = onReady
                        onReady = null
                        notify?.invoke(true)
                    }
                }
            }
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice, requestId: Int, offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                main.post {
                    if (run != generation) return@post
                    try {
                        // Always refuse unauthenticated data reads.
                        server?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                    } catch (_: SecurityException) { stopInternal(false) }
                    catch (_: RuntimeException) { stopInternal(false) }
                }
            }
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
            ) {
                main.post {
                    if (run != generation) return@post
                    if (responseNeeded) {
                        try {
                            server?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
                        } catch (_: SecurityException) { stopInternal(false) }
                        catch (_: RuntimeException) { stopInternal(false) }
                    }
                }
            }
        }
        return try {
            val opened = radioManager.openGattServer(app, callback) ?: return Start.FAILED
            server = opened
            onReady = onRegistered
            val service = BluetoothGattService(serviceId, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            // A marker characteristic with no read or write properties: never expose private data.
            service.addCharacteristic(
                BluetoothGattCharacteristic(
                    UUID.fromString("f63dd7df-68e2-4ffb-b949-a4d403a8ad67"),
                    0, 0
                )
            )
            if (!opened.addService(service)) {
                stopInternal(false)
                Start.FAILED
            } else {
                val task = Runnable { if (run == generation) stopInternal(false) }
                timeout = task
                main.postDelayed(task, 10_000L)
                Start.STARTING
            }
        } catch (_: SecurityException) {
            stopInternal(false)
            Start.PERMISSION_REQUIRED
        } catch (_: RuntimeException) {
            stopInternal(false)
            Start.FAILED
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(): Boolean {
        if (!onMain()) return false
        stopInternal(false)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun stopInternal(reportFailure: Boolean) {
        generation++
        timeout?.let(main::removeCallbacks)
        timeout = null
        val old = server
        server = null
        try { old?.clearServices() } catch (_: RuntimeException) { }
        try { old?.close() } catch (_: RuntimeException) { }
        val notify = onReady
        onReady = null
        if (reportFailure || notify != null) notify?.invoke(false)
    }
}
