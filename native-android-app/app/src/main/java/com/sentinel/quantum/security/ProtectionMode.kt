package com.sentinel.quantum.security

/**
 * User-visible privacy boundary for protection features.
 *
 * LOCAL_ONLY is the fail-closed default: caller numbers, SMS bodies, contacts and local history
 * are not eligible for remote enrichment. ENHANCED permits only feature-specific, explicitly
 * minimised remote enrichment; it never makes OTP-bearing SMS content eligible for upload.
 */
enum class ProtectionMode {
    LOCAL_ONLY,
    ENHANCED
}

object ProtectionModePolicy {
    fun permitsCallerNumberEnrichment(mode: ProtectionMode): Boolean =
        mode == ProtectionMode.ENHANCED

    fun permitsSmsBodyTransmission(
        mode: ProtectionMode,
        otpInspection: SmsOtpPrivacy.Result
    ): Boolean =
        mode == ProtectionMode.ENHANCED &&
            SmsOtpPrivacy.permitsRemoteTransmission(otpInspection)

    fun permitsExplicitCommunityReport(mode: ProtectionMode): Boolean =
        mode == ProtectionMode.ENHANCED

    fun permitsContactsTransmission(mode: ProtectionMode): Boolean = false

    fun permitsCallHistoryTransmission(mode: ProtectionMode): Boolean = false
}
