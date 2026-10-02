package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSendCallbackPolicyTest {
    private val token = "123e4567-e89b-42d3-a456-426614174000"

    private fun valid() = MmsSendCallbackPolicy.Input(
        action = MmsSendCoordinator.ACTION_SEND_COMPLETE,
        scheme = "sentinel-mms-send",
        host = "callback",
        pathSegments = listOf(token),
        uriToken = token,
        extraToken = token,
        fileName = token + ".pdu",
        subscriptionId = 1
    )

    @Test fun acceptsIdentityBoundCallback() {
        assertTrue(MmsSendCallbackPolicy.accepts(valid()))
    }

    @Test fun rejectsTokenMismatch() {
        assertFalse(MmsSendCallbackPolicy.accepts(valid().copy(extraToken = "223e4567-e89b-42d3-a456-426614174000")))
    }

    @Test fun rejectsPathTraversalFileName() {
        assertFalse(MmsSendCallbackPolicy.accepts(valid().copy(fileName = "../" + token + ".pdu")))
    }

    @Test fun rejectsInvalidSubscription() {
        assertFalse(MmsSendCallbackPolicy.accepts(valid().copy(subscriptionId = -1)))
    }

    @Test fun rejectsMalformedUuid() {
        assertFalse(MmsSendCallbackPolicy.accepts(valid().copy(
            pathSegments = listOf("not-a-token"),
            uriToken = "not-a-token",
            extraToken = "not-a-token",
            fileName = "not-a-token.pdu"
        )))
    }
}
