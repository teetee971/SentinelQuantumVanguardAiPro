package com.sentinel.quantum.security

import java.util.concurrent.atomic.AtomicInteger

/**
 * Per-call ordering boundary between the mandatory Telecom response and optional background work.
 *
 * A new instance must be created for every onScreenCall() invocation. The boundary never owns an
 * executor; it only delegates after the platform response has returned successfully. Response
 * delivery itself is one-shot so a refactor cannot answer the same Telecom callback twice.
 */
internal class CallScreeningResponseBoundary(
    private val submitter: (Runnable) -> Unit
) {
    private val state = AtomicInteger(STATE_PENDING)

    fun deliverResponse(response: () -> Unit) {
        if (!state.compareAndSet(STATE_PENDING, STATE_DELIVERING)) return
        try {
            response()
            state.set(STATE_DELIVERED)
        } catch (failure: Throwable) {
            // The platform outcome is unknown after an exception. Never retry automatically: a
            // second attempt could become a duplicate Telecom response. Optional work stays closed.
            state.set(STATE_FAILED)
            throw failure
        }
    }

    fun submitPostResponse(block: () -> Unit): Boolean {
        if (state.get() != STATE_DELIVERED) return false
        return runCatching {
            submitter(Runnable(block))
        }.isSuccess
    }

    private companion object {
        const val STATE_PENDING = 0
        const val STATE_DELIVERING = 1
        const val STATE_DELIVERED = 2
        const val STATE_FAILED = 3
    }
}
