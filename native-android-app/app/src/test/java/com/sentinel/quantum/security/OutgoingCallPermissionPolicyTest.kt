package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingCallPermissionPolicyTest {
    @Test fun explicitPlatformDenialBlocksCallPlacement() {
        assertFalse(
            OutgoingCallPermissionPolicy.mayPlaceCall(
                OutgoingCallPermissionPolicy.State.DENIED
            )
        )
    }

    @Test fun unreadableModernOracleBlocksCallPlacement() {
        assertFalse(
            OutgoingCallPermissionPolicy.mayPlaceCall(
                OutgoingCallPermissionPolicy.State.UNKNOWN
            )
        )
    }

    @Test fun explicitAllowancePermitsCallPlacement() {
        assertTrue(
            OutgoingCallPermissionPolicy.mayPlaceCall(
                OutgoingCallPermissionPolicy.State.ALLOWED
            )
        )
    }

    @Test fun legacyApiWithoutOracleKeepsTelecomFallback() {
        assertTrue(
            OutgoingCallPermissionPolicy.mayPlaceCall(
                OutgoingCallPermissionPolicy.State.API_NOT_SUPPORTED
            )
        )
    }
}
