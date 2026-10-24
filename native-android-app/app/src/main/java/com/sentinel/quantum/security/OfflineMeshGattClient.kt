package com.sentinel.quantum.security

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Explicit, client-only experimental GATT connection. No characteristic reads,
 * writes, pairing, identity assertions, encryption, or background connection.
 * A compatible connectable GATT server is not yet implemented by Sentinel.
 * All public methods are main-thread only; link state is never authentication.
 */
class OfflineMeshGattClient(context: Context) {
    enum class Start { STARTED, BUSY, PERMISSION_REQUIRED, WRONG_THREAD, FAILED }
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val gate = OfflineMeshGattLinkGate()
    private var gatt: BluetoothGatt? = null
    private var epoch = 0L
    private var stopListener: ((String) -> Unit)? = null
    private var watchdog: Runnable? = null

    private val serviceId = UUID.fromString("de939bda-5b41-4c5d-8bb7-58c24458a6e2")

    private fun mainThread() = Looper.myLooper() == Looper.getMainLooper()
    private fun permitted(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, onClosed: (String) -> Unit = {}): Start {
        if (!mainThread()) return Start.WRONG_THREAD
        if (gatt != null || gate.stage() != OfflineMeshGattLinkGate.Stage.CLOSED) return Start.BUSY
        if (!permitted()) return Start.PERMISSION_REQUIRED
        val generation = gate.begin() ?: return Start.BUSY
        val session = ++epoch
        stopListener = onClosed
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(client: BluetoothGatt, status: Int, newState: Int) {
                main.post {
                    if (session != epoch || client !== gatt) return@post
                    if (status != BluetoothGatt.GATT_SUCCESS || newState != BluetoothProfile.STATE_CONNECTED) {
                        terminate("DISCONNECTED")
                    } else if (!gate.connected(generation, true)) {
                        terminate("STATE_MISMATCH")
                    } else {
                        try {
                            if (!permitted() || !client.discoverServices()) terminate("DISCOVERY_START_FAILED")
                        } catch (_: SecurityException) {
                            terminate("PERMISSION_REVOKED")
                        } catch (_: RuntimeException) {
                            terminate("DISCOVERY_START_FAILED")
                        }
                    }
                }
            }

            override fun onServicesDiscovered(client: BluetoothGatt, status: Int) {
                main.post {
                    if (session != epoch || client !== gatt) return@post
                    val found = status == BluetoothGatt.GATT_SUCCESS &&
                        client.getService(serviceId) != null
                    if (!gate.servicesDiscovered(generation, found)) {
                        terminate("SERVICE_INVALID")
                    } else {
                        try {
                            if (!permitted() || !client.requestMtu(247)) terminate("MTU_REQUEST_FAILED")
                        } catch (_: SecurityException) {
                            terminate("PERMISSION_REVOKED")
                        } catch (_: RuntimeException) {
                            terminate("MTU_REQUEST_FAILED")
                        }
                    }
                }
            }

            override fun onMtuChanged(client: BluetoothGatt, mtu: Int, status: Int) {
                main.post {
                    if (session != epoch || client !== gatt) return@post
                    if (!gate.mtuResolved(generation, mtu, status == BluetoothGatt.GATT_SUCCESS)) {
                        terminate("MTU_FAILED")
                    }
                    // TRANSPORT_READY is not authenticated and cannot send private messages.
                }
            }
        }
        return try {
            // No autoConnect; user initiation only.
            val opened = device.connectGatt(app, false, callback)
            if (opened == null) {
                terminate("CONNECT_START_FAILED")
                Start.FAILED
            } else {
                gatt = opened
                val timer = Runnable { if (session == epoch) terminate("TIMEOUT") }
                watchdog = timer
                main.postDelayed(timer, 15_000L)
                Start.STARTED
            }
        } catch (_: SecurityException) {
            terminate("PERMISSION_REVOKED")
            Start.PERMISSION_REQUIRED
        } catch (_: RuntimeException) {
            terminate("CONNECT_START_FAILED")
            Start.FAILED
        }
    }

    @SuppressLint("MissingPermission")
    fun close(): Boolean {
        if (!mainThread()) return false
        terminate("USER_CLOSED")
        return true
    }

    @SuppressLint("MissingPermission")
    private fun terminate(reason: String) {
        epoch++
        gate.close()
        watchdog?.let(main::removeCallbacks)
        watchdog = null
        val old = gatt
        gatt = null
        try { old?.disconnect() } catch (_: RuntimeException) { }
        try { old?.close() } catch (_: RuntimeException) { }
        val listener = stopListener
        stopListener = null
        listener?.invoke(reason)
    }
}
