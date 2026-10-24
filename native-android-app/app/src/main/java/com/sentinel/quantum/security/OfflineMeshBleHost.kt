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
    enum class State { STOPPED, REGISTERING_SERVICE, ADVERTISING_REQUESTED }
    enum class Start { STARTING, BUSY, WRONG_THREAD, FAILED }
    private val server = OfflineMeshGattServer(context)
    private val advertiser = OfflineMeshConnectableAdvertiser(context)
    private var state = State.STOPPED
    private var generation = 0L
    private var listener: ((String) -> Unit)? = null

    fun state(): State = state

    fun start(onStopped: (String) -> Unit = {}): Start {
        if (Looper.myLooper() != Looper.getMainLooper()) return Start.WRONG_THREAD
        if (state != State.STOPPED) return Start.BUSY
        val run = ++generation
        listener = onStopped
        state = State.REGISTERING_SERVICE
        val outcome = server.start { registered ->
            if (generation == run && state == State.REGISTERING_SERVICE) {
                if (!registered) {
                    shutdown("SERVICE_REGISTRATION_FAILED")
                } else {
                    state = State.ADVERTISING_REQUESTED
                    val adv = advertiser.start { reason ->
                        if (generation == run && state != State.STOPPED) {
                            shutdown("ADVERTISING_" + reason)
                        }
                    }
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
        if (state == State.STOPPED) return
        generation++
        state = State.STOPPED
        advertiser.stop()
        server.stop()
        val notify = listener
        listener = null
        notify?.invoke(reason)
    }
}
