package com.sentinel.quantum.data

/**
 * Transient, in-process holder for text shared into the app via the Android Share Sheet
 * (`ACTION_SEND`, `text/plain`). The value is never persisted or sent anywhere; it is consumed
 * exactly once by [com.sentinel.quantum.ui.screens.EmailSecurityScreen] and then discarded.
 */
object SharedTextHolder {
    private const val MAX_LENGTH = 256 * 1024

    @Volatile
    private var pending: String? = null

    /** Offers newly shared text, replacing (and discarding) any previous unconsumed value. */
    fun offer(text: String?) {
        pending = text?.take(MAX_LENGTH)?.takeIf(String::isNotBlank)
    }

    /** Returns and clears the pending shared text, if any. */
    fun consume(): String? {
        val value = pending
        pending = null
        return value
    }

    /** Peeks without consuming, used only to pick an initial navigation destination. */
    fun hasPending(): Boolean = pending != null
}
