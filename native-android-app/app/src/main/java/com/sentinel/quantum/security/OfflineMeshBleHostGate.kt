package com.sentinel.quantum.security

/**
 * Pure-state authority for service registration and advertising callbacks.
 * All calls must be serialized by the Android host on its main thread.
 * READY means advertising confirmed, NOT a connected or authenticated peer.
 */
class OfflineMeshBleHostGate {
    enum class State { STOPPED, REGISTERING, STARTING_ADVERTISING, ADVERTISING }
    private var state = State.STOPPED
    private var generation = 0L
    fun state(): State = state
    fun generation(): Long = generation

    fun begin(): Long? {
        if (state != State.STOPPED) return null
        generation++
        state = State.REGISTERING
        return generation
    }

    fun serviceRegistered(callbackGeneration: Long, success: Boolean): Boolean {
        if (callbackGeneration != generation || state != State.REGISTERING) return false
        if (!success) { stop(); return false }
        state = State.STARTING_ADVERTISING
        return true
    }

    fun advertisingStarted(callbackGeneration: Long, success: Boolean): Boolean {
        if (callbackGeneration != generation || state != State.STARTING_ADVERTISING) return false
        if (!success) { stop(); return false }
        state = State.ADVERTISING
        return true
    }

    fun stop() {
        generation++
        state = State.STOPPED
    }
}
