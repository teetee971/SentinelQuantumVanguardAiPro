package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRoleActivationGateTest {
    @Test fun incompleteSoftwareCannotRequestRole() {
        assertFalse(
            SmsRoleActivationGate.canRequestRole(
                availableCapabilities = setOf(SmsClientCapability.RECEIVE_SMS),
                isDefaultSmsHandler = false
            )
        )
    }

    @Test fun completeSoftwareCanRequestRoleBeforePhysicalValidation() {
        assertTrue(
            SmsRoleActivationGate.canRequestRole(
                availableCapabilities = SmsRoleMigrationPolicy.requiredCapabilities,
                isDefaultSmsHandler = false
            )
        )
    }

    @Test fun alreadyDefaultHandlerDoesNotRequestRoleAgain() {
        assertFalse(
            SmsRoleActivationGate.canRequestRole(
                availableCapabilities = SmsRoleMigrationPolicy.requiredCapabilities,
                isDefaultSmsHandler = true
            )
        )
    }
}
