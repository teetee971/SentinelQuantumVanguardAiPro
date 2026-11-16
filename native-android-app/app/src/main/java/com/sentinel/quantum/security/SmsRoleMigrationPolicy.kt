package com.sentinel.quantum.security

/**
 * Fail-closed gate for default-SMS activation and validation.
 *
 * The sequence is deliberate:
 * 1) software capabilities must be complete;
 * 2) ROLE_SMS may then be requested so real-device tests are possible;
 * 3) while Sentinel actually holds ROLE_SMS, SMS permissions may be used for those tests;
 * 4) physical-device evidence and distribution/Play review remain separate gates before the
 *    product can be described as fully validated for release.
 */
enum class SmsClientCapability {
    RECEIVE_SMS,
    READ_CONVERSATIONS,
    SEND_SMS,
    NOTIFICATIONS,
    MMS_ATTACHMENTS,
    EMERGENCY_MESSAGES,
    MULTI_SIM,
    LOCAL_RETENTION,
    EXPORT_AND_DELETE,
    OFFLINE_ANALYSIS
}

enum class SmsMigrationStage {
    MANUAL_SCANNER_ONLY,
    CLIENT_INCOMPLETE,
    ELIGIBLE_FOR_ROLE_REQUEST,
    DEVICE_VALIDATION_REQUIRED,
    DISTRIBUTION_REVIEW_REQUIRED,
    ACTIVE_DEFAULT_HANDLER
}

data class SmsMigrationAssessment(
    val stage: SmsMigrationStage,
    val missingCapabilities: Set<SmsClientCapability>,
    val roleRequestAllowed: Boolean,
    val smsPermissionsAllowed: Boolean
)

object SmsRoleMigrationPolicy {
    val requiredCapabilities: Set<SmsClientCapability> = SmsClientCapability.entries.toSet()

    /** Software capabilities present before physical-device/carrier validation. */
    val implementedCapabilities: Set<SmsClientCapability> = setOf(
        SmsClientCapability.RECEIVE_SMS,
        SmsClientCapability.READ_CONVERSATIONS,
        SmsClientCapability.SEND_SMS,
        SmsClientCapability.NOTIFICATIONS,
        SmsClientCapability.MMS_ATTACHMENTS,
        SmsClientCapability.EMERGENCY_MESSAGES,
        SmsClientCapability.MULTI_SIM,
        SmsClientCapability.LOCAL_RETENTION,
        SmsClientCapability.EXPORT_AND_DELETE,
        SmsClientCapability.OFFLINE_ANALYSIS
    )

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
                smsPermissionsAllowed = false
            )
        }

        // Physical validation cannot precede ROLE_SMS because real SMS/MMS tests require the role.
        if (!isDefaultSmsHandler) {
            return SmsMigrationAssessment(
                stage = SmsMigrationStage.ELIGIBLE_FOR_ROLE_REQUEST,
                missingCapabilities = emptySet(),
                roleRequestAllowed = true,
                smsPermissionsAllowed = false
            )
        }

        // Once Android confirms ROLE_SMS, permissions can be used for the explicit device tests.
        if (!physicalDeviceValidationPassed) {
            return SmsMigrationAssessment(
                stage = SmsMigrationStage.DEVICE_VALIDATION_REQUIRED,
                missingCapabilities = emptySet(),
                roleRequestAllowed = false,
                smsPermissionsAllowed = true
            )
        }

        // Distribution review is a release gate, not a prerequisite for testing on the device.
        if (!playPolicyReviewReady) {
            return SmsMigrationAssessment(
                stage = SmsMigrationStage.DISTRIBUTION_REVIEW_REQUIRED,
                missingCapabilities = emptySet(),
                roleRequestAllowed = false,
                smsPermissionsAllowed = true
            )
        }

        return SmsMigrationAssessment(
            stage = SmsMigrationStage.ACTIVE_DEFAULT_HANDLER,
            missingCapabilities = emptySet(),
            roleRequestAllowed = false,
            smsPermissionsAllowed = true
        )
    }
}
