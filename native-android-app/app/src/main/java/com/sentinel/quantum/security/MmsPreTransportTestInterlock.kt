package com.sentinel.quantum.security

import com.sentinel.quantum.BuildConfig

/** Debug-only synchronization seam for final MMS authorization race instrumentation. */
internal object MmsPreTransportTestInterlock {
    @Volatile
    private var hook: (() -> Unit)? = null

    fun installForInstrumentation(value: (() -> Unit)?) {
        check(BuildConfig.DEBUG) { "MMS transport interlock is debug-only" }
        hook = value
    }

    fun beforeFinalAuthorizationRecheck() {
        if (!BuildConfig.DEBUG) return
        hook?.invoke()
    }
}
