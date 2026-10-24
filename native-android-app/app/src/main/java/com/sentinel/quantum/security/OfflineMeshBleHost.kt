package com.sentinel.quantum.security

import android.content.Context
import android.os.Looper

/**
 * Opt-in marker-only BLE coordinator. The server must register successfully before
 * advertising starts; a failure on either component stops both. No message path.
 *
 * Instances are not connected to application startup. Main-thread only.
 */
class OfflineMeshBleHost(context: Context) {
    enum class Start { STARTING, BUSY, WRONG_THREAD, FAILED }
    private val server = OfflineMeshGattServer(context)
    private val advertiser = OfflineMeshConnectableAdvertiser(context)
    private val gate = OfflineMeshBleHostGate()
    private var listener: ((String) -> Unit)? = null

    fun state(): OfflineMeshBleHostGate.State = gate.state()

    fun start(onStopped: (String) -> Unit = {}): Start {
        if (Looper.myLooper() != Looper.getMainLooper()) return Start.WRONG_THREAD
        val run = gate.begin() ?: return Start.BUSY
        listener = onStopped
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

    private fun shutdown(reason: String) {
        if (gate.state() == OfflineMeshBleHostGate.State.STOPPED && listener == null) return
        gate.stop()
        advertiser.stop()
        server.stop()
        val notify = listener
        listener = null
        notify?.invoke(reason)
    }
}
