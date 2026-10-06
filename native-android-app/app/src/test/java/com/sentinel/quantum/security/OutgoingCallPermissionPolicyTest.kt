package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingCallPermissionPolicyTest {
    @Test
    fun frameworkSimCapabilityIsAccepted() {
        assertEquals(
            OutgoingCallPermissionPolicy.AccountAuthority.FRAMEWORK_SIM,
            OutgoingCallPermissionPolicy.classify(frameworkSimCapability = true)
        )
        assertTrue(
            OutgoingCallPermissionPolicy.mayPlacePstnCall(
                OutgoingCallPermissionPolicy.AccountAuthority.FRAMEWORK_SIM
            )
        )
    }

    @Test
    fun missingFrameworkSimCapabilityFailsClosed() {
        assertEquals(
            OutgoingCallPermissionPolicy.AccountAuthority.UNVERIFIED,
            OutgoingCallPermissionPolicy.classify(frameworkSimCapability = false)
        )
        assertFalse(
            OutgoingCallPermissionPolicy.mayPlacePstnCall(
                OutgoingCallPermissionPolicy.AccountAuthority.UNVERIFIED
            )
        )
    }
}
