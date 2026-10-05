package com.sentinel.quantum.security

/**
 * Fail-closed decision boundary for TelecomManager.isOutgoingCallPermitted().
 *
 * Android 8.0+ exposes an explicit oracle for a selected PhoneAccount. A negative answer or an
 * unreadable oracle must never be converted into a call attempt. On API 24-25 the oracle does not
 * exist, so the already validated active PhoneAccount is handed to Telecom and the platform owns
 * the final routing decision.
 */
object OutgoingCallPermissionPolicy {
    enum class State { ALLOWED, DENIED, UNKNOWN, API_NOT_SUPPORTED }

    fun mayPlaceCall(state: State): Boolean =
        state == State.ALLOWED || state == State.API_NOT_SUPPORTED
}
