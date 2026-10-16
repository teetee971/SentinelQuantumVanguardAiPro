package com.sentinel.quantum.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Coordinates one process-wide warm-up without allowing screening to wait indefinitely. */
internal class CallScreeningWarmupCoordinator(
    private val loader: () -> Unit,
    private val dispatch: ((Runnable) -> Unit)
) {
    enum class State { NOT_STARTED, LOADING, READY, FAILED }

    private val state = AtomicReference(State.NOT_STARTED)
    private val completion = CountDownLatch(1)

    fun start(): Boolean {
        if (!state.compareAndSet(State.NOT_STARTED, State.LOADING)) return false
        return runCatching {
            dispatch(Runnable {
                try {
                    loader()
                    state.set(State.READY)
                } catch (_: Throwable) {
                    state.set(State.FAILED)
                } finally {
                    completion.countDown()
                }
            })
            true
        }.getOrElse {
            state.set(State.FAILED)
            completion.countDown()
            false
        }
    }

    fun awaitReady(timeoutMs: Long): Boolean {
        if (state.get() == State.READY) return true
        if (state.get() == State.FAILED || state.get() == State.NOT_STARTED) return false
        if (timeoutMs <= 0L) return false
        return try {
            completion.await(timeoutMs, TimeUnit.MILLISECONDS) && state.get() == State.READY
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    fun state(): State = state.get()
}
