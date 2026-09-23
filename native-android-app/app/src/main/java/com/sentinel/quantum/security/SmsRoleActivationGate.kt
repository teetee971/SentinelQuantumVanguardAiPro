package com.sentinel.quantum.security

/**
 * Pure bridge between the implemented SMS software capability set and the Android ROLE_SMS UI.
 * Physical-device and distribution gates intentionally happen after the role is granted.
 */
object SmsRoleActivationGate {
    fun canRequestRole(
        availableCapabilities: Set<SmsClientCapability> = SmsRoleMigrationPolicy.implementedCapabilities,
        isDefaultSmsHandler: Boolean
    ): Boolean =
        SmsRoleMigrationPolicy.assess(
            availableCapabilities = availableCapabilities,
            physicalDeviceValidationPassed = false,
            playPolicyReviewReady = false,
            isDefaultSmsHandler = isDefaultSmsHandler
        ).roleRequestAllowed
}
