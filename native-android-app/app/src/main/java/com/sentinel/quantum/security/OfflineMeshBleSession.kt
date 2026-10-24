package com.sentinel.quantum.security

/**
 * Pure state machine for a single opt-in Offline Mesh BLE session.
 * No claim of connectivity is possible until authenticated transport proof is reported.
 * Unlike the WireGuard Mesh controller, this state concerns local BLE radio only.
 */
class OfflineMeshBleSession {
    enum class State { STOPPED, READY_TO_START, DISCOVERING, LINK_ESTABLISHED, AUTHENTICATED, LIMITED, LOCKED }
    private var state = State.STOPPED
    private var generation = 0L

    fun state(): State = state
    fun generation(): Long = generation

    fun prepare(readiness: OfflineMeshBleReadiness.Result): State {
        generation++
        state = when (readiness.status) {
            OfflineMeshBleReadiness.Status.READY -> State.READY_TO_START
            OfflineMeshBleReadiness.Status.LIMITED -> State.LIMITED
            OfflineMeshBleReadiness.Status.LOCKED -> State.LOCKED
        }
        return state
    }

    fun start(): Long? {
        if (state != State.READY_TO_START) return null
        state = State.DISCOVERING
        return generation
    }

    fun linkConnected(callbackGeneration: Long): Boolean {
        if (state != State.DISCOVERING || callbackGeneration != generation) return false
        state = State.LINK_ESTABLISHED
        return true
    }

    fun authenticated(callbackGeneration: Long, verified: Boolean): Boolean {
        if (state != State.LINK_ESTABLISHED || callbackGeneration != generation || !verified) return false
        state = State.AUTHENTICATED
        return true
    }

    fun stop() {
        generation++
        state = State.STOPPED
    }

    fun radioLost() {
        generation++
        state = State.LIMITED
    }

    fun canSendPrivateMessages(): Boolean = state == State.AUTHENTICATED
}
