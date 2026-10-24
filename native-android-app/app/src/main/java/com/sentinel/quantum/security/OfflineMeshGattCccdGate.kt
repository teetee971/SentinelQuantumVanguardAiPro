package com.sentinel.quantum.security

/**
 * Pure Kotlin CCCD write/ack guard. A subscription is NEVER authentication.
 * Android callbacks must be checked against the active GATT instance and epoch
 * before forwarding events here.
 */
internal class OfflineMeshGattCccdGate {
    enum class State { IDLE, PENDING, SUBSCRIBED }
    private var state = State.IDLE
    private var epoch = -1L

    fun state(): State = state

    fun begin(newEpoch: Long): Boolean {
        if (newEpoch < 0 || newEpoch <= epoch) return false
        epoch = newEpoch
        state = State.IDLE
        return true
    }

    fun started(callbackEpoch: Long): Boolean {
        if (callbackEpoch != epoch || state != State.IDLE) return false
        state = State.PENDING
        return true
    }

    fun completed(callbackEpoch: Long, correctDescriptor: Boolean, successful: Boolean): Boolean {
        if (callbackEpoch != epoch || state != State.PENDING ||
            !correctDescriptor || !successful) {
            return false
        }
        state = State.SUBSCRIBED
        return true
    }

    fun clear() { state = State.IDLE }

    fun privateDataAuthorized(): Boolean = false
}
