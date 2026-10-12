package com.sentinel.quantum.security

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-stable admission guard for the composer transport boundary.
 *
 * Activity/Compose recreation must not manufacture a fresh unlocked transport gate while the
 * previous owner's submission is still crossing Android telephony. All gate instances therefore
 * share one process-local admission bit. This remains intentionally narrower than delivery-state
 * tracking: SENT/DELIVERED callback ownership is handled by the existing message identity/status
 * pipeline, not by this short-lived submission guard.
 */
class SmsSubmissionGate {
    fun tryAcquire(): Boolean = IN_FLIGHT.compareAndSet(false, true)

    fun release() {
        IN_FLIGHT.set(false)
    }

    companion object {
        private val IN_FLIGHT = AtomicBoolean(false)

        internal fun isInFlight(): Boolean = IN_FLIGHT.get()
    }
}
