package com.sentinel.quantum.security

/**
 * Pure truth policy for in-call actions and legacy call direction.
 *
 * Android capability flags are converted to bounded booleans before they reach the UI.
 */
object InCallTruthPolicy {
    enum class LegacyState {
        RINGING,
        DIALING,
        CONNECTING,
        SELECT_PHONE_ACCOUNT,
        OTHER
    }

    fun legacyDirection(state: LegacyState): String = when (state) {
        LegacyState.RINGING -> "INCOMING"
        LegacyState.DIALING,
        LegacyState.CONNECTING,
        LegacyState.SELECT_PHONE_ACCOUNT -> "OUTGOING"
        LegacyState.OTHER -> "UNKNOWN"
    }

    fun canToggleHold(currentHoldCapability: Boolean, genericConference: Boolean): Boolean =
        currentHoldCapability && !genericConference

    fun canMute(currentMuteCapability: Boolean): Boolean = currentMuteCapability
}
