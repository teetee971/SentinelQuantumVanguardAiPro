package com.sentinel.quantum.security

/**
 * Pure Kotlin guard for a single connected GATT link. This does NOT establish
 * trust; transport callbacks must be checked against the current generation
 * before forwarding any data to a higher layer.
 */
class OfflineMeshGattLinkGate {
    enum class Stage { CLOSED, CONNECTING, DISCOVERING, NEGOTIATING_MTU, TRANSPORT_READY }
    private var stage = Stage.CLOSED
    private var generation = 0L
    private var payloadLimit = 0

    fun stage(): Stage = stage
    fun generation(): Long = generation
    fun maxWriteBytes(): Int = payloadLimit

    fun begin(): Long? {
        if (stage != Stage.CLOSED) return null
        generation++
        stage = Stage.CONNECTING
        payloadLimit = 0
        return generation
    }

    fun connected(callbackGeneration: Long, success: Boolean): Boolean {
        if (callbackGeneration != generation || stage != Stage.CONNECTING) return false
        if (!success) { close(); return false }
        stage = Stage.DISCOVERING
        return true
    }

    fun servicesDiscovered(callbackGeneration: Long, serviceAndCharacteristicValid: Boolean): Boolean {
        if (callbackGeneration != generation || stage != Stage.DISCOVERING) return false
        if (!serviceAndCharacteristicValid) { close(); return false }
        stage = Stage.NEGOTIATING_MTU
        return true
    }

    /** ATT write payload overhead is three bytes; cap oversized values. */
    fun mtuResolved(callbackGeneration: Long, mtu: Int, success: Boolean): Boolean {
        if (callbackGeneration != generation || stage != Stage.NEGOTIATING_MTU) return false
        if (!success || mtu !in 23..517) { close(); return false }
        payloadLimit = (mtu - 3).coerceAtMost(244)
        stage = Stage.TRANSPORT_READY
        return true
    }

    fun mayWrite(callbackGeneration: Long, bytes: Int): Boolean =
        callbackGeneration == generation &&
            stage == Stage.TRANSPORT_READY &&
            bytes in 1..payloadLimit

    fun close() {
        generation++
        stage = Stage.CLOSED
        payloadLimit = 0
    }
}
