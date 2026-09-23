package com.sentinel.quantum.security

/**
 * Fail-closed gate for the future default-SMS migration.
 *
 * This policy does not request a role or a permission. It prevents the UI from doing so
 * until a complete messaging client, device validation and Play policy preparation exist.
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
    DEVICE_VALIDATION_REQUIRED,
    ELIGIBLE_FOR_ROLE_REQUEST,
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

    /**
     * Software capabilities implemented by the staged client. Physical-device validation and
     * Play policy readiness remain independent gates before ROLE_SMS can be requested.
     */
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

        if (!physicalDeviceValidationPassed) {
            return SmsMigrationAssessment(
                stage = SmsMigrationStage.DEVICE_VALIDATION_REQUIRED,
                missingCapabilities = emptySet(),
                roleRequestAllowed = false,
                smsPermissionsAllowed = false
            )
        }

        if (!playPolicyReviewReady) {
            return SmsMigrationAssessment(
                stage = SmsMigrationStage.DEVICE_VALIDATION_REQUIRED,
                missingCapabilities = emptySet(),
                roleRequestAllowed = false,
                smsPermissionsAllowed = false
            )
        }

        if (!isDefaultSmsHandler) {
            return SmsMigrationAssessment(
                stage = SmsMigrationStage.ELIGIBLE_FOR_ROLE_REQUEST,
                missingCapabilities = emptySet(),
                roleRequestAllowed = true,
                smsPermissionsAllowed = false
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
