package com.sentinel.quantum.security

/**
 * Defines the only UI events that may refresh SMS activation/SIM state.
 *
 * Refreshing is read-only: it never grants a role or permission. Keeping this policy explicit
 * prevents the composer from caching stale role/permission/SIM state after returning from Android
 * system UI while avoiding unrelated recomposition-triggered telephony lookups.
 */
object SmsActivationRefreshPolicy {
    enum class Event {
        INITIAL_LOAD,
        ACTIVITY_RESUMED,
        SMS_ROLE_RESULT,
        RUNTIME_PERMISSION_RESULT,
        USER_RETRY,
        UNRELATED_UI_CHANGE
    }

    fun shouldRefresh(event: Event): Boolean = when (event) {
        Event.INITIAL_LOAD,
        Event.ACTIVITY_RESUMED,
        Event.SMS_ROLE_RESULT,
        Event.RUNTIME_PERMISSION_RESULT,
        Event.USER_RETRY -> true
        Event.UNRELATED_UI_CHANGE -> false
    }
}
