package com.sentinel.quantum.security

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Per-call ordering boundary between the mandatory Telecom response and optional background work.
 *
 * A new instance must be created for every onScreenCall() invocation. The boundary never owns an
 * executor; it only delegates after the platform response has returned successfully.
 */
internal class CallScreeningResponseBoundary(
    private val submitter: (Runnable) -> Unit
) {
    private val responseDelivered = AtomicBoolean(false)

    fun deliverResponse(response: () -> Unit) {
        response()
        responseDelivered.set(true)
    }

    fun submitPostResponse(block: () -> Unit): Boolean {
        if (!responseDelivered.get()) return false
        return runCatching {
            submitter(Runnable(block))
        }.isSuccess
    }
}
