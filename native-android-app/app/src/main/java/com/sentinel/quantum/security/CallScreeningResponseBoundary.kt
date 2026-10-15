package com.sentinel.quantum.security

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicit ordering boundary between the synchronous Telecom response and optional work.
 *
 * The response marker is set only after respondToCall() returns. Submission before that marker
 * is rejected, so a future caller cannot accidentally put persistence or maintenance in front of
 * the platform response.
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
        return runCatching { submitter(Runnable(block)) }.isSuccess
    }

}
