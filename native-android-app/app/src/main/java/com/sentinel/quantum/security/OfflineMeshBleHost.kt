package com.sentinel.quantum.security

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Opt-in BLE coordinator. The server must register successfully before advertising
 * starts; a failure on either component stops both. Its default server has no
 * identity/trust factory, so it remains unable to authorize private messages.
 *
 * Instances are not connected to application startup. Main-thread only.
 */
class OfflineMeshBleHost(
    context: Context,
    transportFactory: ((peerKey: String, generation: Long) -> OfflineMeshGattSecureTransport?)? = null,
    onMessage: (peerKey: String, payload: ByteArray) -> Unit = { _, _ -> }
) {
    enum class Start { STARTING, BUSY, WRONG_THREAD, FAILED }
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val bluetooth = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private var monitor: Runnable? = null
    private val server = OfflineMeshGattServer(context, transportFactory, onMessage)
    private val advertiser = OfflineMeshConnectableAdvertiser(context)
    private val gate = OfflineMeshBleHostGate()
    private var listener: ((String) -> Unit)? = null

    fun state(): OfflineMeshBleHostGate.State = gate.state()

    fun start(onStopped: (String) -> Unit = {}): Start {
        if (Looper.myLooper() != Looper.getMainLooper()) return Start.WRONG_THREAD
        val run = gate.begin() ?: return Start.BUSY
        listener = onStopped
        monitorRadio(run)
        val outcome = server.start { registered ->
            if (gate.generation() == run && gate.state() == OfflineMeshBleHostGate.State.REGISTERING) {
                if (!gate.serviceRegistered(run, registered)) {
                    shutdown("SERVICE_REGISTRATION_FAILED")
                } else {
                    val adv = advertiser.start(
                        stopped = { reason ->
                            if (gate.generation() == run && gate.state() != OfflineMeshBleHostGate.State.STOPPED) {
                                shutdown("ADVERTISING_" + reason)
                            }
                        },
                        started = {
                            if (gate.generation() == run && !gate.advertisingStarted(run, true)) {
                                shutdown("ADVERTISING_STATE_MISMATCH")
                            }
                        }
                    )
                    if (adv != OfflineMeshConnectableAdvertiser.Start.STARTING) {
                        shutdown("ADVERTISING_START_REJECTED")
                    }
                }
            }
        }
        if (outcome != OfflineMeshGattServer.Start.STARTING) {
            shutdown("SERVER_START_REJECTED")
            return Start.FAILED
        }
        return Start.STARTING
    }

    fun stop(): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) return false
        shutdown("USER_STOPPED")
        return true
    }

    fun send(peerKey: String, payload: ByteArray): OfflineMeshGattServer.Send =
        if (Looper.myLooper() != Looper.getMainLooper()) {
            OfflineMeshGattServer.Send.WRONG_THREAD
        } else {
            server.send(peerKey, payload)
        }

    private fun monitorRadio(run: Long) {
        val tick = object : Runnable {
            override fun run() {
                if (gate.generation() != run || gate.state() == OfflineMeshBleHostGate.State.STOPPED) return
                val permissionsOk = Build.VERSION.SDK_INT < 31 ||
                    (ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                     ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED)
                val radioOk = try { permissionsOk && bluetooth?.adapter?.isEnabled == true }
                    catch (_: SecurityException) { false }
                if (!radioOk) shutdown("RADIO_OR_PERMISSION_LOST")
                else main.postDelayed(this, 1_000L)
            }
        }
        monitor = tick
        main.postDelayed(tick, 1_000L)
    }

    private fun shutdown(reason: String) {
        if (gate.state() == OfflineMeshBleHostGate.State.STOPPED && listener == null) return
        gate.stop()
        monitor?.let(main::removeCallbacks)
        monitor = null
        advertiser.stop()
        server.stop()
        val notify = listener
        listener = null
        notify?.invoke(reason)
    }
}
