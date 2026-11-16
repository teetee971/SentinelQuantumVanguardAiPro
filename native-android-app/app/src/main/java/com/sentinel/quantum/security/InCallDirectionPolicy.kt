package com.sentinel.quantum.security

/** Call direction is learned once per Telecom call, independently of foreground selection. */
internal object InCallDirectionPolicy {
    fun reconcile(previous: String?, observed: String): String = when {
        previous == "INCOMING" || previous == "OUTGOING" -> previous
        observed == "INCOMING" || observed == "OUTGOING" -> observed
        else -> "UNKNOWN"
    }
}
