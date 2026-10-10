package com.sentinel.quantum.security

import com.sentinel.quantum.BuildConfig

/**
 * Debug-only synchronization seam used by Android instrumentation to mutate authorization at the
 * exact final pre-transport boundary. Release builds can neither install nor execute the hook.
 */
internal object SmsPreTransportTestInterlock {
    @Volatile
    private var hook: (() -> Unit)? = null

    fun installForInstrumentation(value: (() -> Unit)?) {
        check(BuildConfig.DEBUG) { "Phone Core transport interlock is debug-only" }
        hook = value
    }

    fun beforeFinalAuthorizationRecheck() {
        if (!BuildConfig.DEBUG) return
        hook?.invoke()
    }
}
