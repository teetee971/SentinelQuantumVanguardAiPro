package com.sentinel.quantum.security

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Configuration-stable owner for composer submission admission.
 *
 * The ViewModel outlives Activity recreation, so a rotation cannot replace the atomic gate while
 * the previous Activity's submission coroutine is still crossing the Android telephony boundary.
 */
internal class SmsSubmissionViewModel : ViewModel() {
    private val gate = SmsSubmissionGate()
    private val _inFlight = MutableStateFlow(false)

    val inFlight: StateFlow<Boolean> = _inFlight.asStateFlow()

    fun tryAcquire(): Boolean {
        if (!gate.tryAcquire()) return false
        _inFlight.value = true
        return true
    }

    fun release() {
        gate.release()
        _inFlight.value = false
    }
}
