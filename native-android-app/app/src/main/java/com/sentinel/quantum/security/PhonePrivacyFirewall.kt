package com.sentinel.quantum.security

/**
 * Explicit egress policy for Phone Core data classes.
 * LOCAL_ONLY is the fail-closed default.
 */
object PhonePrivacyFirewall {
    enum class Mode { LOCAL_ONLY, ENHANCED }
    enum class DataClass { CONTACTS, CALL_HISTORY, SMS_BODY, OTP, PHONE_NUMBER, REPUTATION_QUERY }
    data class Decision(val mayLeaveDevice: Boolean, val reason: String)

    fun decide(mode: Mode, dataClass: DataClass, explicitConsent: Boolean): Decision {
        if (dataClass in NEVER_REMOTE) return Decision(false, "LOCAL_ONLY_DATA_CLASS")
        if (mode != Mode.ENHANCED) return Decision(false, "LOCAL_ONLY_MODE")
        if (!explicitConsent) return Decision(false, "EXPLICIT_CONSENT_REQUIRED")
        return Decision(true, "ENHANCED_EXPLICIT_CONSENT")
    }

    private val NEVER_REMOTE = setOf(
        DataClass.CONTACTS,
        DataClass.CALL_HISTORY,
        DataClass.OTP
    )
}
