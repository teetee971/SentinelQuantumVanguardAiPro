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
    private val serviceId = OfflineMeshGattProtocolIds.SERVICE
    private var server: BluetoothGattServer? = null
    private var generation = 0L
    private var onReady: ((Boolean) -> Unit)? = null
    private var timeout: Runnable? = null
    // Keys are local connection handles, not cryptographic identities.
    private val peerSessions = OfflineMeshGattPeerSessions()
    private val peerTokens = mutableMapOf<String, OfflineMeshGattPeerSessions.Token>()
    // Android callbacks have no connection ID. After a disconnect, fail closed
    // for this address until the server is restarted.
    private val retiredPeerKeys = mutableSetOf<String>()

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
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                main.post {
                    if (run != generation || server == null) return@post
                    val key = try { device.address } catch (_: SecurityException) { return@post }
                    when {
                        status != BluetoothGatt.GATT_SUCCESS ||
                            newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED ->
                            {
                                retiredPeerKeys.add(key)
                                peerTokens.remove(key)?.let(peerSessions::disconnect)
                            }
                        newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                            if (!permitted() || key in retiredPeerKeys) return@post
                            if (!peerTokens.containsKey(key)) {
                                peerSessions.connect(key)?.let { peerTokens[key] = it }
                            }
                        }
                    }
                    // A GATT connection never grants private data access.
                }
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                main.post {
                    if (run != generation || server == null) return@post
                    val key = try { device.address } catch (_: SecurityException) { return@post }
                    if (key !in retiredPeerKeys) peerTokens[key]?.let { peerSessions.updateMtu(it, mtu) }
                }
            }
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
            override fun onDescriptorReadRequest(
                device: BluetoothDevice, requestId: Int, offset: Int,
                descriptor: android.bluetooth.BluetoothGattDescriptor
            ) {
                main.post {
                    if (run != generation || server == null) return@post
                    try {
                        server?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                    } catch (_: SecurityException) { stopInternal(false) }
                    catch (_: RuntimeException) { stopInternal(false) }
                }
            }
            override fun onDescriptorWriteRequest(
                device: BluetoothDevice, requestId: Int,
                descriptor: android.bluetooth.BluetoothGattDescriptor,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
            ) {
                main.post {
                    if (run != generation || server == null) return@post
                    if (responseNeeded) {
                        try {
                            server?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
                        } catch (_: SecurityException) { stopInternal(false) }
                        catch (_: RuntimeException) { stopInternal(false) }
                    }
                }
            }
            override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
                main.post {
                    if (run != generation || server == null) return@post
                    try {
                        server?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
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
                    OfflineMeshGattProtocolIds.MARKER,
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
        peerTokens.clear()
        retiredPeerKeys.clear()
        peerSessions.close()
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
