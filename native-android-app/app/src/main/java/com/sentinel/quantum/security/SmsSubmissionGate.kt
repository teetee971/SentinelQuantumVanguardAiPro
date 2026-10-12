package com.sentinel.quantum.security

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-stable admission guard for the composer transport boundary.
 *
 * Activity/Compose recreation must not manufacture a fresh unlocked transport gate while the
 * previous owner's submission is still crossing Android telephony. All gate instances therefore
 * share one process-local admission bit. The observable state mirrors that same process-wide bit
 * so recreated UI owners cannot visually advertise a submission path that transport admission will
 * reject. This remains intentionally narrower than delivery-state tracking: SENT/DELIVERED callback
 * ownership is handled by the existing message identity/status pipeline, not by this short-lived
 * submission guard.
 */
class SmsSubmissionGate {
    val inFlight: StateFlow<Boolean>
        get() = IN_FLIGHT_STATE.asStateFlow()

    fun tryAcquire(): Boolean {
        val acquired = IN_FLIGHT.compareAndSet(false, true)
        if (acquired) IN_FLIGHT_STATE.value = true
        return acquired
    }

    fun release() {
        IN_FLIGHT.set(false)
        IN_FLIGHT_STATE.value = false
    }

    companion object {
        private val IN_FLIGHT = AtomicBoolean(false)
        private val IN_FLIGHT_STATE = MutableStateFlow(false)

        internal fun isInFlight(): Boolean = IN_FLIGHT.get()
    }
}
