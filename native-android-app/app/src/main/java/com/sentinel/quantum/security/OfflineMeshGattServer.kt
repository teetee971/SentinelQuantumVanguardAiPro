package com.sentinel.quantum.security

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * Peripheral-side GATT transport. Service registration is only a radio/setup
 * result. The server accepts application bytes only through a supplied secure
 * channel factory and emits notifications only after CCCD plus cryptographic
 * authentication for that peer.
 */
class OfflineMeshGattServer(
    context: Context,
    private val transportFactory: ((peerKey: String, generation: Long) -> OfflineMeshGattSecureTransport?)? = null,
    private val onMessage: (peerKey: String, payload: ByteArray) -> Unit = { _, _ -> }
) {
    enum class Start { STARTING, BUSY, PERMISSION_REQUIRED, UNAVAILABLE, WRONG_THREAD, FAILED }
    enum class Send { QUEUED, NOT_AUTHENTICATED, FULL, INVALID, NO_PEER, WRONG_THREAD }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val manager = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val serviceId = OfflineMeshGattProtocolIds.SERVICE
    private var server: BluetoothGattServer? = null
    private var generation = 0L
    private var onReady: ((Boolean) -> Unit)? = null
    private var timeout: Runnable? = null
    private var transportTimer: Runnable? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var clientWriteCharacteristic: BluetoothGattCharacteristic? = null
    // Keys are local connection handles, not cryptographic identities.
    private val peerSessions = OfflineMeshGattPeerSessions()
    private val peerTokens = mutableMapOf<String, OfflineMeshGattPeerSessions.Token>()
    private val transports = mutableMapOf<String, OfflineMeshGattSecureTransport>()
    private val notificationInFlight = mutableMapOf<String, OfflineMeshGattSecureTransport.Outbound>()
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
            private fun current(): Boolean = run == generation && server != null

            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                main.post {
                    if (!current()) return@post
                    val key = peerKey(device) ?: return@post
                    when {
                        status != BluetoothGatt.GATT_SUCCESS ||
                            newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                            retiredPeerKeys.add(key)
                            removePeer(key)
                        }
                        newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                            if (!permitted() || key in retiredPeerKeys || peerTokens.containsKey(key)) return@post
                            val token = peerSessions.connect(key) ?: return@post
                            peerTokens[key] = token
                            if (!createTransport(key, token)) {
                                peerSessions.disconnect(token)
                            }
                        }
                    }
                }
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                main.post {
                    if (!current()) return@post
                    val key = peerKey(device) ?: return@post
                    val token = peerTokens[key] ?: return@post
                    if (key !in retiredPeerKeys && peerSessions.updateMtu(token, mtu)) {
                        pumpPeer(key)
                    }
                }
            }

            override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                main.post {
                    if (!current()) return@post
                    if (service.uuid != serviceId || status != BluetoothGatt.GATT_SUCCESS) {
                        stopInternal(false)
                    } else {
                        timeout?.let(main::removeCallbacks)
                        timeout = null
                        val notify = onReady
                        onReady = null
                        // This callback means only that the GATT service was registered.
                        // It is not an authentication or private-transport READY state.
                        notify?.invoke(true)
                    }
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                main.post {
                    if (!current()) return@post
                    sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                }
            }

            override fun onDescriptorReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                descriptor: android.bluetooth.BluetoothGattDescriptor
            ) {
                main.post {
                    if (!current()) return@post
                    sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: android.bluetooth.BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                main.post {
                    if (!current()) return@post
                    val key = peerKey(device)
                    val token = key?.let(peerTokens::get)
                    val isCccd = descriptor.uuid == OfflineMeshGattProtocolIds.CCCD &&
                        descriptor.characteristic === notifyCharacteristic
                    val enable = value.contentEquals(
                        android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                    val disable = value.contentEquals(
                        android.bluetooth.BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    )
                    val peerKey = key ?: ""
                    val peerToken = token
                    val valid = isCccd && !preparedWrite && offset == 0 &&
                        peerKey.isNotEmpty() && peerKey !in retiredPeerKeys &&
                        permitted() && (enable || disable)
                    var accepted = false
                    if (valid && peerToken != null) {
                        accepted = peerSessions.setNotifications(peerToken, enable)
                        if (accepted) {
                            if (enable) {
                                if (!createTransport(peerKey, peerToken)) {
                                    peerSessions.setNotifications(peerToken, false)
                                    accepted = false
                                }
                            } else {
                                transports.remove(peerKey)?.interrupt()
                                notificationInFlight.remove(peerKey)
                            }
                            pumpPeer(peerKey)
                        }
                    }
                    if (responseNeeded) {
                        sendResponse(
                            device,
                            requestId,
                            if (accepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_WRITE_NOT_PERMITTED,
                            0,
                            null
                        )
                    }
                }
            }

            override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
                main.post {
                    if (!current()) return@post
                    sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                main.post {
                    if (!current()) return@post
                    val key = peerKey(device)
                    val token = key?.let(peerTokens::get)
                    val accepted = if (
                        key != null && token != null && key !in retiredPeerKeys && permitted() &&
                        !preparedWrite && offset == 0 &&
                        characteristic.uuid == OfflineMeshGattProtocolIds.CLIENT_TO_SERVER &&
                        characteristic === clientWriteCharacteristic
                    ) {
                        val channel = transports[key]
                        val inbound = channel?.receive(value.copyOf(), SystemClock.elapsedRealtime())
                        if (inbound?.accepted == true) {
                            peerSessions.setPrivateDataAllowed(token, channel.isAuthenticated())
                            inbound.complete?.let { onMessage(key, it.copyOf()) }
                            pumpPeer(key)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                    if (responseNeeded) {
                        sendResponse(
                            device,
                            requestId,
                            if (accepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_WRITE_NOT_PERMITTED,
                            offset,
                            null
                        )
                    }
                }
            }

            override fun onNotificationSent(device: BluetoothDevice, status: Int) {
                main.post {
                    if (!current()) return@post
                    val key = peerKey(device) ?: return@post
                    val sent = notificationInFlight.remove(key) ?: return@post
                    val token = peerTokens[key] ?: return@post
                    val channel = transports[key] ?: return@post
                    val accepted = channel.onRadioWriteComplete(
                        sent.ticket,
                        token.generation,
                        status == BluetoothGatt.GATT_SUCCESS,
                        SystemClock.elapsedRealtime()
                    )
                    if (!accepted || status != BluetoothGatt.GATT_SUCCESS) {
                        channel.interrupt()
                        transports.remove(key)
                    } else {
                        pumpPeer(key)
                    }
                }
            }
        }
        return try {
            val opened = radioManager.openGattServer(app, callback) ?: return Start.FAILED
            server = opened
            onReady = onRegistered
            val service = BluetoothGattService(serviceId, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(BluetoothGattCharacteristic(OfflineMeshGattProtocolIds.MARKER, 0, 0))
            clientWriteCharacteristic = BluetoothGattCharacteristic(
                OfflineMeshGattProtocolIds.CLIENT_TO_SERVER,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(clientWriteCharacteristic)
            notifyCharacteristic = BluetoothGattCharacteristic(
                OfflineMeshGattProtocolIds.SERVER_TO_CLIENT,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0
            )
            notifyCharacteristic!!.addDescriptor(
                android.bluetooth.BluetoothGattDescriptor(
                    OfflineMeshGattProtocolIds.CCCD,
                    android.bluetooth.BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
            service.addCharacteristic(notifyCharacteristic)
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

    fun send(peerKey: String, payload: ByteArray): Send {
        if (!onMain()) return Send.WRONG_THREAD
        val token = peerTokens[peerKey] ?: return Send.NO_PEER
        val channel = transports[peerKey] ?: return Send.NOT_AUTHENTICATED
        if (!channel.isAuthenticated() || !peerSessions.canTransferPrivateData(token)) {
            return Send.NOT_AUTHENTICATED
        }
        return when (channel.offer(payload)) {
            OfflineMeshGattTransport.Offer.QUEUED -> {
                pumpPeer(peerKey)
                scheduleTransportTimer()
                Send.QUEUED
            }
            OfflineMeshGattTransport.Offer.FULL -> Send.FULL
            OfflineMeshGattTransport.Offer.INVALID -> Send.INVALID
            OfflineMeshGattTransport.Offer.LOCKED -> Send.NOT_AUTHENTICATED
        }
    }

    fun authenticated(peerKey: String): Boolean =
        onMain() && transports[peerKey]?.isAuthenticated() == true

    @SuppressLint("MissingPermission")
    fun stop(): Boolean {
        if (!onMain()) return false
        stopInternal(false)
        return true
    }

    private fun peerKey(device: BluetoothDevice): String? =
        runCatching { device.address }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun createTransport(
        key: String,
        token: OfflineMeshGattPeerSessions.Token
    ): Boolean {
        transports.remove(key)?.interrupt()
        notificationInFlight.remove(key)
        val channel = try {
            transportFactory?.invoke(key, token.generation)
        } catch (_: RuntimeException) {
            null
        }
        if (transportFactory != null && channel == null) return false
        if (channel != null && !channel.startLink(token.generation)) return false
        if (channel != null) transports[key] = channel
        return true
    }

    private fun sendResponse(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray?
    ) {
        try {
            server?.sendResponse(device, requestId, status, offset, value)
        } catch (_: SecurityException) {
            stopInternal(false)
        } catch (_: RuntimeException) {
            stopInternal(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun pumpPeer(key: String) {
        val token = peerTokens[key] ?: return
        val channel = transports[key] ?: return
        peerSessions.setPrivateDataAllowed(token, channel.isAuthenticated())
        val snapshot = peerSessions.snapshot(token) ?: return
        if (!snapshot.notificationsEnabled || notificationInFlight.containsKey(key)) return
        when (val tick = channel.tick(SystemClock.elapsedRealtime())) {
            is OfflineMeshGattSecureTransport.Tick.Interrupted -> {
                channel.interrupt()
                transports.remove(key)
                return
            }
            is OfflineMeshGattSecureTransport.Tick.Retry -> sendNotification(key, token, tick.outbound)
            OfflineMeshGattSecureTransport.Tick.Idle -> {
                channel.next(SystemClock.elapsedRealtime())?.let { sendNotification(key, token, it) }
            }
        }
        scheduleTransportTimer()
    }

    @SuppressLint("MissingPermission")
    private fun sendNotification(
        key: String,
        token: OfflineMeshGattPeerSessions.Token,
        outbound: OfflineMeshGattSecureTransport.Outbound
    ) {
        peerSessions.snapshot(token) ?: return
        if (!peerSessions.canSendControlNotification(token, outbound.bytes.size)) {
            transports.remove(key)?.interrupt()
            return
        }
        val device = try {
            manager?.adapter?.getRemoteDevice(key)
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val gattServer = server
        val characteristic = notifyCharacteristic
        if (device == null || gattServer == null || characteristic == null || !permitted()) return
        notificationInFlight[key] = outbound
        try {
            val started = if (Build.VERSION.SDK_INT >= 33) {
                gattServer.notifyCharacteristicChanged(device, characteristic, false, outbound.bytes) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = outbound.bytes.copyOf()
                @Suppress("DEPRECATION")
                gattServer.notifyCharacteristicChanged(device, characteristic, false)
            }
            if (!started) {
                notificationInFlight.remove(key)
                transports.remove(key)?.interrupt()
            }
        } catch (_: SecurityException) {
            notificationInFlight.remove(key)
            transports.remove(key)?.interrupt()
        } catch (_: RuntimeException) {
            notificationInFlight.remove(key)
            transports.remove(key)?.interrupt()
        }
    }

    private fun scheduleTransportTimer() {
        if (transportTimer != null) return
        val timer = Runnable {
            transportTimer = null
            if (server != null) {
                peerTokens.keys.toList().forEach(::pumpPeer)
                if (transports.isNotEmpty()) scheduleTransportTimer()
            }
        }
        transportTimer = timer
        main.postDelayed(timer, 100L)
    }

    private fun removePeer(key: String) {
        notificationInFlight.remove(key)
        transports.remove(key)?.interrupt()
        peerTokens.remove(key)?.let(peerSessions::disconnect)
    }

    @SuppressLint("MissingPermission")
    private fun stopInternal(reportFailure: Boolean) {
        generation++
        peerTokens.clear()
        retiredPeerKeys.clear()
        notificationInFlight.clear()
        transports.values.forEach(OfflineMeshGattSecureTransport::interrupt)
        transports.clear()
        peerSessions.close()
        timeout?.let(main::removeCallbacks)
        timeout = null
        transportTimer?.let(main::removeCallbacks)
        transportTimer = null
        val old = server
        server = null
        notifyCharacteristic = null
        clientWriteCharacteristic = null
        try {
            old?.clearServices()
        } catch (_: RuntimeException) {
        }
        try {
            old?.close()
        } catch (_: RuntimeException) {
        }
        val notify = onReady
        onReady = null
        if (reportFailure || notify != null) notify?.invoke(false)
    }
}
