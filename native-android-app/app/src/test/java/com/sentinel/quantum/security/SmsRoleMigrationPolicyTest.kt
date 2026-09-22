package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRoleMigrationPolicyTest {
    @Test
    fun emptyClientRemainsManualScannerWithoutSmsPermissions() {
        val result = SmsRoleMigrationPolicy.assess(
            availableCapabilities = emptySet(),
            physicalDeviceValidationPassed = false,
            playPolicyReviewReady = false,
            isDefaultSmsHandler = false
        )

        assertEquals(SmsMigrationStage.MANUAL_SCANNER_ONLY, result.stage)
        assertFalse(result.roleRequestAllowed)
        assertFalse(result.smsPermissionsAllowed)
        assertEquals(SmsRoleMigrationPolicy.requiredCapabilities, result.missingCapabilities)
    }

    @Test
    fun incompleteClientCannotRequestRoleEvenIfPolicyFlagsAreTrue() {
        val result = SmsRoleMigrationPolicy.assess(
            availableCapabilities = setOf(SmsClientCapability.RECEIVE_SMS),
            physicalDeviceValidationPassed = true,
            playPolicyReviewReady = true,
            isDefaultSmsHandler = false
        )

        assertEquals(SmsMigrationStage.CLIENT_INCOMPLETE, result.stage)
        assertFalse(result.roleRequestAllowed)
        assertFalse(result.smsPermissionsAllowed)
    }

    @Test
    fun stagedImplementationStillCannotRequestDefaultSmsRole() {
        val result = SmsRoleMigrationPolicy.assess(
            availableCapabilities = SmsRoleMigrationPolicy.implementedCapabilities,
            physicalDeviceValidationPassed = true,
            playPolicyReviewReady = true,
            isDefaultSmsHandler = false
        )

        assertEquals(SmsMigrationStage.CLIENT_INCOMPLETE, result.stage)
        assertFalse(result.roleRequestAllowed)
        assertFalse(result.smsPermissionsAllowed)
        assertEquals(
            setOf(SmsClientCapability.MMS_ATTACHMENTS),
            result.missingCapabilities
        )
    }

    @Test
    fun completeClientStillRequiresPhysicalDeviceValidation() {
        val result = SmsRoleMigrationPolicy.assess(
            availableCapabilities = SmsRoleMigrationPolicy.requiredCapabilities,
            physicalDeviceValidationPassed = false,
            playPolicyReviewReady = true,
            isDefaultSmsHandler = false
        )

        assertEquals(SmsMigrationStage.DEVICE_VALIDATION_REQUIRED, result.stage)
        assertFalse(result.roleRequestAllowed)
        assertFalse(result.smsPermissionsAllowed)
    }

    @Test
    fun eligibleClientMayRequestRoleButNotSmsPermissionsYet() {
        val result = SmsRoleMigrationPolicy.assess(
            availableCapabilities = SmsRoleMigrationPolicy.requiredCapabilities,
            physicalDeviceValidationPassed = true,
            playPolicyReviewReady = true,
            isDefaultSmsHandler = false
        )

        assertEquals(SmsMigrationStage.ELIGIBLE_FOR_ROLE_REQUEST, result.stage)
        assertTrue(result.roleRequestAllowed)
        assertFalse(result.smsPermissionsAllowed)
    }

    @Test
    fun permissionsAreAllowedOnlyWhileActivelyDefaultHandler() {
        val result = SmsRoleMigrationPolicy.assess(
            availableCapabilities = SmsRoleMigrationPolicy.requiredCapabilities,
            physicalDeviceValidationPassed = true,
            playPolicyReviewReady = true,
            isDefaultSmsHandler = true
        )

        assertEquals(SmsMigrationStage.ACTIVE_DEFAULT_HANDLER, result.stage)
        assertFalse(result.roleRequestAllowed)
        assertTrue(result.smsPermissionsAllowed)
    }
}
