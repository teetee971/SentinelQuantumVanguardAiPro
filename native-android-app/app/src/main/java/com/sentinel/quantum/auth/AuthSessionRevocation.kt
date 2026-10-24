package com.sentinel.quantum.auth

import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local notification of authentication revocation.
 * Subscribers must perform asynchronous teardown themselves.
 */
internal object AuthSessionRevocation {
    private val epoch = AtomicLong(0L)
    private val listeners = CopyOnWriteArraySet<(Long) -> Unit>()

    fun current(): Long = epoch.get()

    fun isCurrent(value: Long): Boolean = epoch.get() == value

    fun subscribe(listener: (Long) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun revoke(): Long {
        val value = epoch.incrementAndGet()
        listeners.forEach { listener ->
            runCatching { listener(value) }
        }
        return value
    }
}
