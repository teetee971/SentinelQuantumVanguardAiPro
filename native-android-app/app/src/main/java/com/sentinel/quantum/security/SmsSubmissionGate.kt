package com.sentinel.quantum.security

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Admission guard for the composer transport boundary.
 *
 * The UI disables its send actions while this guard is held, but the atomic admission check is
 * still required because two click callbacks can arrive before Compose has recomposed the UI.
 */
class SmsSubmissionGate {
    private val inFlight = AtomicBoolean(false)

    fun tryAcquire(): Boolean = inFlight.compareAndSet(false, true)

    fun release() {
        inFlight.set(false)
    }
}
