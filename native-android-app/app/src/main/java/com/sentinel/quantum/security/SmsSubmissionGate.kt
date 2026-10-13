package com.sentinel.quantum.security

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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
    val inFlight: State<Boolean>
        get() = MUTABLE_IN_FLIGHT

    fun tryAcquire(): Boolean {
        if (!IN_FLIGHT.compareAndSet(false, true)) return false
        MUTABLE_IN_FLIGHT.value = true
        return true
    }

    fun release() {
        IN_FLIGHT.set(false)
        MUTABLE_IN_FLIGHT.value = false
    }

    companion object {
        private val IN_FLIGHT = AtomicBoolean(false)
        private val MUTABLE_IN_FLIGHT = mutableStateOf(false)

        internal fun isInFlight(): Boolean {
            // Register a Compose snapshot read for callers invoked during composition while keeping
            // the AtomicBoolean as the authoritative transport admission state.
            MUTABLE_IN_FLIGHT.value
            return IN_FLIGHT.get()
        }
    }
}
