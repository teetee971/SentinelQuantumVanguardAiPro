package com.sentinel.quantum.security

/**
 * Copy shown on the locked screen for a call notification.
 *
 * The unlocked notification may name the caller. This text is what a public
 * lock-screen version is allowed to show.
 */
internal object IncomingCallLockScreen {
    const val MASKED_INCOMING = "Appel entrant"
    const val MASKED_ONGOING = "Appel en cours"

    fun publicText(incoming: Boolean, displayName: String?, handle: String?): String {
        val label = displayName?.takeIf { it.isNotBlank() }
            ?: handle?.takeIf { it.isNotBlank() }
            ?: if (incoming) MASKED_INCOMING else MASKED_ONGOING
        return label
    }
}
