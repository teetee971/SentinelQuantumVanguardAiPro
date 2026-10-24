package com.sentinel.quantum.security

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * Client-side GATT transport. Link setup, MTU negotiation, CCCD subscription,
 * and write callbacks are transport prerequisites only; private data is
 * accepted or emitted only through an explicitly supplied authenticated channel
 * factory. Without that factory this class remains a setup-only endpoint.
 *
 * All public methods and callback state transitions are confined to the main
 * looper. The epoch and exact GATT/characteristic objects reject late callbacks
 * from a previous connection.
 */
class OfflineMeshGattClient(
    context: Context,
    private val transportFactory: ((generation: Long) -> OfflineMeshGattSecureTransport?)? = null,
    private val onMessage: (ByteArray) -> Unit = {}
) {
    enum class Start { STARTED, BUSY, PERMISSION_REQUIRED, WRONG_THREAD, FAILED }
    enum class Send { QUEUED, NOT_AUTHENTICATED, FULL, INVALID, WRONG_THREAD }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val gate = OfflineMeshGattLinkGate()
    private var gatt: BluetoothGatt? = null
    private var epoch = 0L
    private var stopListener: ((String) -> Unit)? = null
    private var watchdog: Runnable? = null
    private var transportTimer: Runnable? = null
    private var subscriptionDescriptor: android.bluetooth.BluetoothGattDescriptor? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notificationCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingWrite: OfflineMeshGattSecureTransport.Outbound? = null
    private var pendingWriteStartedAt: Long? = null
    private val cccdGate = OfflineMeshGattCccdGate()
    private var transport: OfflineMeshGattSecureTransport? = null

    fun notificationsSubscribed(): Boolean =
        mainThread() && cccdGate.state() == OfflineMeshGattCccdGate.State.SUBSCRIBED

    fun authenticated(): Boolean = mainThread() && transport?.isAuthenticated() == true

    private val serviceId = OfflineMeshGattProtocolIds.SERVICE
    private val markerId = OfflineMeshGattProtocolIds.MARKER

    private fun mainThread() = Looper.myLooper() == Looper.getMainLooper()
    private fun permitted(): Boolean = Build.VERSION.SDK_INT < 31 ||
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
        transport = null
        pendingWrite = null
        val callback = object : BluetoothGattCallback() {
            private fun current(client: BluetoothGatt): Boolean = session == epoch && client === gatt

            override fun onConnectionStateChange(client: BluetoothGatt, status: Int, newState: Int) {
                main.post {
                    if (!current(client)) return@post
                    if (status != BluetoothGatt.GATT_SUCCESS ||
                        newState == BluetoothProfile.STATE_DISCONNECTED
                    ) {
                        terminate("DISCONNECTED")
                    } else if (newState != BluetoothProfile.STATE_CONNECTED) {
                        // CONNECTING / DISCONNECTING callbacks are not authorization.
                        return@post
                    } else if (!gate.connected(generation, true)) {
                        terminate("STATE_MISMATCH")
                    } else {
                        try {
                            if (!permitted() || !client.discoverServices()) {
                                terminate("DISCOVERY_START_FAILED")
                            }
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
                    if (!current(client)) return@post
                    val found = if (status != BluetoothGatt.GATT_SUCCESS) false else try {
                        val service = client.getService(serviceId)
                        val marker = service?.getCharacteristic(markerId)
                        val notify = service?.getCharacteristic(OfflineMeshGattProtocolIds.SERVER_TO_CLIENT)
                        val write = service?.getCharacteristic(OfflineMeshGattProtocolIds.CLIENT_TO_SERVER)
                        val descriptor = notify?.getDescriptor(OfflineMeshGattProtocolIds.CCCD)
                        val valid = marker != null && marker.properties == 0 && marker.permissions == 0 &&
                            notify != null &&
                            (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 &&
                            descriptor != null &&
                            write != null &&
                            (write.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 &&
                            (write.permissions and BluetoothGattCharacteristic.PERMISSION_WRITE) != 0
                        subscriptionDescriptor = if (valid) descriptor else null
                        writeCharacteristic = if (valid) write else null
                        notificationCharacteristic = if (valid) notify else null
                        valid
                    } catch (_: SecurityException) {
                        false
                    } catch (_: RuntimeException) {
                        false
                    }
                    if (!gate.servicesDiscovered(generation, found)) {
                        terminate("SERVICE_INVALID")
                    } else {
                        try {
                            if (!permitted() || !client.requestMtu(247)) {
                                terminate("MTU_REQUEST_FAILED")
                            }
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
                    if (!current(client)) return@post
                    if (!gate.mtuResolved(generation, mtu, status == BluetoothGatt.GATT_SUCCESS)) {
                        terminate("MTU_FAILED")
                        return@post
                    }
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
                            ) == BluetoothStatusCodes.SUCCESS
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
            }

            override fun onDescriptorWrite(
                client: BluetoothGatt,
                descriptor: android.bluetooth.BluetoothGattDescriptor,
                status: Int
            ) {
                main.post {
                    if (!current(client)) return@post
                    if (!permitted() || !cccdGate.completed(
                            session,
                            descriptor === subscriptionDescriptor,
                            status == BluetoothGatt.GATT_SUCCESS
                        )
                    ) {
                        terminate("CCCD_WRITE_FAILED")
                        return@post
                    }
                    watchdog?.let(main::removeCallbacks)
                    watchdog = null
                    val channel = transportFactory?.invoke(generation)
                    if (transportFactory != null && channel == null) {
                        terminate("TRANSPORT_FACTORY_FAILED")
                        return@post
                    }
                    transport = channel
                    if (channel != null && !channel.startLink(generation, SystemClock.elapsedRealtime())) {
                        terminate("TRANSPORT_START_FAILED")
                        return@post
                    }
                    pumpTransport(session)
                    scheduleTransportTimer(session)
                }
            }

            override fun onCharacteristicChanged(
                client: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                main.post {
                    if (!current(client) || characteristic !== notificationCharacteristic) return@post
                    receiveNotification(session, value.copyOf())
                }
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onCharacteristicChanged(
                client: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                main.post {
                    if (!current(client) || characteristic !== notificationCharacteristic) return@post
                    val value = characteristic.value ?: return@post
                    receiveNotification(session, value.copyOf())
                }
            }

            override fun onCharacteristicWrite(
                client: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                main.post {
                    if (!current(client) || characteristic !== writeCharacteristic) return@post
                    val sent = pendingWrite ?: return@post
                    pendingWrite = null
                    pendingWriteStartedAt = null
                    val channel = transport ?: return@post
                    val accepted = channel.onRadioWriteComplete(
                        sent.ticket,
                        generation,
                        status == BluetoothGatt.GATT_SUCCESS,
                        SystemClock.elapsedRealtime()
                    )
                    if (!accepted) {
                        terminate(if (status == BluetoothGatt.GATT_SUCCESS) "WRITE_CALLBACK_REJECTED" else "WRITE_FAILED")
                    } else {
                        pumpTransport(session)
                        scheduleTransportTimer(session)
                    }
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

    fun send(payload: ByteArray): Send {
        if (!mainThread()) return Send.WRONG_THREAD
        val channel = transport ?: return Send.NOT_AUTHENTICATED
        if (!channel.isAuthenticated()) return Send.NOT_AUTHENTICATED
        return when (channel.offer(payload)) {
            OfflineMeshGattTransport.Offer.QUEUED -> {
                pumpTransport(epoch)
                scheduleTransportTimer(epoch)
                Send.QUEUED
            }
            OfflineMeshGattTransport.Offer.FULL -> Send.FULL
            OfflineMeshGattTransport.Offer.INVALID -> Send.INVALID
            OfflineMeshGattTransport.Offer.LOCKED -> Send.NOT_AUTHENTICATED
        }
    }

    @SuppressLint("MissingPermission")
    fun close(): Boolean {
        if (!mainThread()) return false
        terminate("USER_CLOSED")
        return true
    }

    private fun receiveNotification(session: Long, bytes: ByteArray) {
        if (session != epoch) return
        val channel = transport ?: run {
            terminate("TRANSPORT_NOT_CONFIGURED")
            return
        }
        val inbound = channel.receive(bytes, SystemClock.elapsedRealtime())
        if (!inbound.accepted) {
            terminate("TRANSPORT_FRAME_REJECTED")
            return
        }
        inbound.complete?.let { onMessage(it.copyOf()) }
        pumpTransport(session)
        scheduleTransportTimer(session)
    }

    @SuppressLint("MissingPermission")
    private fun pumpTransport(session: Long) {
        if (session != epoch || gatt == null) return
        val pending = pendingWrite
        if (pending != null) {
            val startedAt = pendingWriteStartedAt ?: SystemClock.elapsedRealtime()
            val now = SystemClock.elapsedRealtime()
            if (now - startedAt >= RADIO_CONFIRMATION_TIMEOUT_MS) {
                pendingWrite = null
                pendingWriteStartedAt = null
                transport?.onRadioWriteComplete(pending.ticket, session, false, now)
                terminate("RADIO_CONFIRMATION_TIMEOUT")
            }
            return
        }
        val channel = transport ?: return
        when (val tick = channel.tick(SystemClock.elapsedRealtime())) {
            is OfflineMeshGattSecureTransport.Tick.Interrupted -> {
                terminate("TRANSPORT_INTERRUPTED")
                return
            }
            is OfflineMeshGattSecureTransport.Tick.Retry -> {
                writeOutbound(session, tick.outbound)
                return
            }
            OfflineMeshGattSecureTransport.Tick.Idle -> Unit
        }
        channel.next(SystemClock.elapsedRealtime())?.let { writeOutbound(session, it) }
    }

    @SuppressLint("MissingPermission")
    private fun writeOutbound(session: Long, outbound: OfflineMeshGattSecureTransport.Outbound) {
        if (session != epoch || gatt == null || pendingWrite != null) return
        val client = gatt ?: return
        val characteristic = writeCharacteristic ?: run {
            terminate("WRITE_CHARACTERISTIC_UNAVAILABLE")
            return
        }
        if (!gate.mayWrite(session, outbound.bytes.size)) {
            transport?.onRadioWriteComplete(
                outbound.ticket,
                session,
                false,
                SystemClock.elapsedRealtime()
            )
            terminate("MTU_TOO_SMALL")
            return
        }
        if (!permitted()) {
            terminate("PERMISSION_REVOKED")
            return
        }
        pendingWrite = outbound
        pendingWriteStartedAt = SystemClock.elapsedRealtime()
        try {
            val started = if (Build.VERSION.SDK_INT >= 33) {
                client.writeCharacteristic(
                    characteristic,
                    outbound.bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                characteristic.value = outbound.bytes.copyOf()
                @Suppress("DEPRECATION")
                client.writeCharacteristic(characteristic)
            }
            if (!started) {
                pendingWrite = null
                pendingWriteStartedAt = null
                transport?.onRadioWriteComplete(
                    outbound.ticket,
                    session,
                    false,
                    SystemClock.elapsedRealtime()
                )
                terminate("WRITE_START_FAILED")
            }
        } catch (_: SecurityException) {
            pendingWrite = null
            pendingWriteStartedAt = null
            terminate("PERMISSION_REVOKED")
        } catch (_: RuntimeException) {
            pendingWrite = null
            pendingWriteStartedAt = null
            terminate("WRITE_START_FAILED")
        }
    }

    private fun scheduleTransportTimer(session: Long) {
        if (transportTimer != null) return
        val timer = Runnable {
            transportTimer = null
            if (session == epoch && transport != null) {
                pumpTransport(session)
                if (transport != null) scheduleTransportTimer(session)
            }
        }
        transportTimer = timer
        main.postDelayed(timer, 100L)
    }

    @SuppressLint("MissingPermission")
    private fun terminate(reason: String) {
        epoch++
        gate.close()
        subscriptionDescriptor = null
        writeCharacteristic = null
        notificationCharacteristic = null
        cccdGate.clear()
        watchdog?.let(main::removeCallbacks)
        watchdog = null
        transportTimer?.let(main::removeCallbacks)
        transportTimer = null
        pendingWrite = null
        pendingWriteStartedAt = null
        transport?.interrupt()
        transport = null
        val old = gatt
        gatt = null
        try {
            old?.disconnect()
        } catch (_: SecurityException) {
        } catch (_: RuntimeException) {
        }
        try {
            old?.close()
        } catch (_: SecurityException) {
        } catch (_: RuntimeException) {
        }
        val listener = stopListener
        stopListener = null
        listener?.invoke(reason)
    }

    private companion object {
        const val RADIO_CONFIRMATION_TIMEOUT_MS = 5_000L
    }
}
