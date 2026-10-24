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

/**
 * Explicit, client-only experimental GATT connection. No characteristic reads,
 * writes, pairing, identity assertions, encryption, or background connection.
 * The marker-only GATT server cannot accept application data.
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
    private var subscriptionDescriptor: android.bluetooth.BluetoothGattDescriptor? = null
    private val cccdGate = OfflineMeshGattCccdGate()

    fun notificationsSubscribed(): Boolean =
        mainThread() && cccdGate.state() == OfflineMeshGattCccdGate.State.SUBSCRIBED

    private val serviceId = OfflineMeshGattProtocolIds.SERVICE
    private val markerId = OfflineMeshGattProtocolIds.MARKER

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
        if (!cccdGate.begin(session)) {
            terminate("CCCD_GATE_INVALID")
            return Start.FAILED
        }
        stopListener = onClosed
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(client: BluetoothGatt, status: Int, newState: Int) {
                main.post {
                    if (session != epoch || client !== gatt) return@post
                    // Disconnect is terminal. Other successful states are not connection proof.
                    if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                        terminate("DISCONNECTED")
                    } else if (newState != BluetoothProfile.STATE_CONNECTED) {
                        // CONNECTING / DISCONNECTING callbacks are not authorization.
                        return@post
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
                    val found = if (status != BluetoothGatt.GATT_SUCCESS) false else try {
                        val marker = client.getService(serviceId)?.getCharacteristic(markerId)
                        val notify = client.getService(serviceId)?.getCharacteristic(
                            OfflineMeshGattProtocolIds.SERVER_TO_CLIENT)
                        val descriptor = notify?.getDescriptor(OfflineMeshGattProtocolIds.CCCD)
                        val valid = marker != null && marker.properties == 0 && marker.permissions == 0 &&
                            notify != null &&
                            (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 &&
                            descriptor != null
                        subscriptionDescriptor = if (valid) descriptor else null
                        valid
                    } catch (_: SecurityException) { false }
                      catch (_: RuntimeException) { false }
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
                    } else {
                        val descriptor = subscriptionDescriptor
                        val characteristic = descriptor?.characteristic
                        if (descriptor == null || characteristic == null || !permitted()) {
                            terminate("CCCD_UNAVAILABLE")
                            return@post
                        }
                        try {
                            if (!client.setCharacteristicNotification(characteristic, true)) {
                                terminate("NOTIFY_ENABLE_FAILED")
                                return@post
                            }
                            if (!cccdGate.started(session)) {
                                terminate("CCCD_GATE_INVALID")
                                return@post
                            }
                            val started = if (Build.VERSION.SDK_INT >= 33) {
                                client.writeDescriptor(
                                    descriptor,
                                    android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                ) == BluetoothGatt.GATT_SUCCESS
                            } else {
                                @Suppress("DEPRECATION")
                                descriptor.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                client.writeDescriptor(descriptor)
                            }
                            if (!started) terminate("CCCD_WRITE_START_FAILED")
                        } catch (_: SecurityException) {
                            terminate("PERMISSION_REVOKED")
                        } catch (_: RuntimeException) {
                            terminate("CCCD_WRITE_START_FAILED")
                        }
                    }
                    // TRANSPORT_READY is not authenticated and cannot send private messages.
                }
            }
            // No application notification protocol is enabled yet. Receiving bytes
            // must never be interpreted as authenticated content.
            override fun onCharacteristicChanged(
                client: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                main.post {
                    if (session != epoch || client !== gatt) return@post
                    terminate("UNEXPECTED_GATT_NOTIFICATION")
                }
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onCharacteristicChanged(
                client: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                main.post {
                    if (session != epoch || client !== gatt) return@post
                    terminate("UNEXPECTED_GATT_NOTIFICATION")
                }
            }
            override fun onDescriptorWrite(
                client: BluetoothGatt,
                descriptor: android.bluetooth.BluetoothGattDescriptor,
                status: Int
            ) {
                main.post {
                    if (session != epoch || client !== gatt) return@post
                    if (!permitted() || !cccdGate.completed(
                            session,
                            descriptor === subscriptionDescriptor,
                            status == BluetoothGatt.GATT_SUCCESS
                        )) {
                        terminate("CCCD_WRITE_FAILED")
                        return@post
                    }
                    watchdog?.let(main::removeCallbacks)
                    watchdog = null
                }
            }
        }
        return try {
            // No autoConnect; user initiation only.
            @Suppress("DEPRECATION")
            val opened = device.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE)
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
        subscriptionDescriptor = null
        cccdGate.clear()
        watchdog?.let(main::removeCallbacks)
        watchdog = null
        val old = gatt
        gatt = null
        try { old?.disconnect() } catch (_: SecurityException) { }
        catch (_: RuntimeException) { }
        try { old?.close() } catch (_: SecurityException) { }
        catch (_: RuntimeException) { }
        val listener = stopListener
        stopListener = null
        listener?.invoke(reason)
    }
}
