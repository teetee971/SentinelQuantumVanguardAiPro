package com.sentinel.quantum.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Coordinates one process-wide warm-up without allowing screening to wait indefinitely. */
internal class CallScreeningWarmupCoordinator(
    private val loader: () -> Unit,
    private val dispatch: ((Runnable) -> Unit)
) {
    enum class State { NOT_STARTED, LOADING, READY, FAILED }

    private val lock = Any()
    @Volatile private var state = State.NOT_STARTED
    @Volatile private var completion = CountDownLatch(0)

    /**
     * Starts the warm-up once. A transient FAILED state may be retried; READY and LOADING are
     * stable/idempotent. Dispatch itself is non-blocking and performs no storage work inline.
     */
    fun start(): Boolean {
        val attemptCompletion = synchronized(lock) {
            if (state == State.LOADING || state == State.READY) return false
            state = State.LOADING
            CountDownLatch(1).also { completion = it }
        }

        return runCatching {
            dispatch(Runnable {
                try {
                    loader()
                    state = State.READY
                } catch (_: Throwable) {
                    state = State.FAILED
                } finally {
                    attemptCompletion.countDown()
                }
            })
            true
        }.getOrElse {
            state = State.FAILED
            attemptCompletion.countDown()
            false
        }
    }

    fun awaitReady(timeoutMs: Long): Boolean {
        if (state == State.READY) return true
        if (state == State.FAILED || state == State.NOT_STARTED) return false
        if (timeoutMs <= 0L) return false
        val attemptCompletion = completion
        return try {
            attemptCompletion.await(timeoutMs, TimeUnit.MILLISECONDS) && state == State.READY
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    fun state(): State = state
}
