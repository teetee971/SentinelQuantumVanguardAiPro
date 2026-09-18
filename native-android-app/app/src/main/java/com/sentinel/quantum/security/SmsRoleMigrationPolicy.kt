package com.sentinel.quantum.security

/**
 * Fail-closed gate for default-SMS activation.
 *
 * Android role consent is allowed only when the app declares the complete core handler surface.
 * SMS runtime permissions remain forbidden until Android confirms that Sentinel actually holds
 * ROLE_SMS. Physical-device validation and Play policy readiness gate public release, not the
 * user's ability to grant the role required to perform that validation.
 */
enum class SmsClientCapability {
    RECEIVE_SMS,
    RECEIVE_MMS,
    SEND_SMS,
    SENDTO_INTENTS,
    RESPOND_VIA_MESSAGE,
    LOCAL_CONVERSATIONS,
    OFFLINE_ANALYSIS
}

enum class SmsMigrationStage {
    MANUAL_SCANNER_ONLY,
    CLIENT_INCOMPLETE,
    ELIGIBLE_FOR_ROLE_REQUEST,
    ACTIVE_DEFAULT_HANDLER
}

data class SmsMigrationAssessment(
    val stage: SmsMigrationStage,
    val missingCapabilities: Set<SmsClientCapability>,
    val roleRequestAllowed: Boolean,
    val smsPermissionsAllowed: Boolean,
    val publicReleaseReady: Boolean
)

object SmsRoleMigrationPolicy {
    val requiredCapabilities: Set<SmsClientCapability> = SmsClientCapability.entries.toSet()

    fun assess(
        availableCapabilities: Set<SmsClientCapability>,
        physicalDeviceValidationPassed: Boolean,
        playPolicyReviewReady: Boolean,
        isDefaultSmsHandler: Boolean
    ): SmsMigrationAssessment {
        val missing = requiredCapabilities - availableCapabilities
        if (missing.isNotEmpty()) {
            return SmsMigrationAssessment(
                stage = if (availableCapabilities.isEmpty()) {
                    SmsMigrationStage.MANUAL_SCANNER_ONLY
                } else {
                    SmsMigrationStage.CLIENT_INCOMPLETE
                },
                missingCapabilities = missing,
                roleRequestAllowed = false,
                smsPermissionsAllowed = false,
                publicReleaseReady = false
            )
        }

        if (!isDefaultSmsHandler) {
            return SmsMigrationAssessment(
                stage = SmsMigrationStage.ELIGIBLE_FOR_ROLE_REQUEST,
                missingCapabilities = emptySet(),
                roleRequestAllowed = true,
                smsPermissionsAllowed = false,
                publicReleaseReady = false
            )
        }

        return SmsMigrationAssessment(
            stage = SmsMigrationStage.ACTIVE_DEFAULT_HANDLER,
            missingCapabilities = emptySet(),
            roleRequestAllowed = false,
            smsPermissionsAllowed = true,
            publicReleaseReady = physicalDeviceValidationPassed && playPolicyReviewReady
        )
    }
}
