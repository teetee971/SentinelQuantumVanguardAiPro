package com.sentinel.quantum.security

internal object SentinelVpnModeArbiter {
    enum class Mode { INTERNET_VPN, PRIVATE_MESH }

    private var activeMode: Mode? = null

    @Synchronized
    fun acquire(mode: Mode): Boolean {
        val current = activeMode
        if (current != null && current != mode) return false
        activeMode = mode
        return true
    }

    @Synchronized
    fun release(mode: Mode) {
        if (activeMode == mode) activeMode = null
    }

    @Synchronized
    fun current(): Mode? = activeMode

    @Synchronized
    internal fun resetForTests() {
        activeMode = null
    }
}
