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

    fun publicText(
        incoming: Boolean,
        @Suppress("UNUSED_PARAMETER") displayName: String?,
        @Suppress("UNUSED_PARAMETER") handle: String?
    ): String = if (incoming) MASKED_INCOMING else MASKED_ONGOING
}
