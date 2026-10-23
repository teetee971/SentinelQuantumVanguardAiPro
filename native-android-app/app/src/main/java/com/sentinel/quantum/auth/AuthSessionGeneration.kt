package com.sentinel.quantum.auth

internal class AuthSessionGeneration {
    private var generation = 0L

    @Synchronized
    fun current(): Long = generation

    @Synchronized
    fun begin(): Long = ++generation

    @Synchronized
    fun invalidate(): Long = ++generation

    @Synchronized
    fun isCurrent(value: Long): Boolean =
        value == generation

    @Synchronized
    fun runIfCurrent(value: Long, action: () -> Unit): Boolean {
        if (value != generation) return false
        action()
        return true
    }

    @Synchronized
    fun invalidateAndRun(action: () -> Unit) {
        ++generation
        action()
    }
}
