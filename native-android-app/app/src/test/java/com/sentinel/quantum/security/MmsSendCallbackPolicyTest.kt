package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSendCallbackPolicyTest {
    // Constructed fixtures keep UUID syntax coverage without storing secret-like high-entropy literals.
    private val token = listOf("00000000", "0000", "4000", "8000", "000000000000").joinToString("-")
    private val otherToken = listOf("11111111", "1111", "4111", "8111", "111111111111").joinToString("-")

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
        assertFalse(MmsSendCallbackPolicy.accepts(valid().copy(extraToken = otherToken)))
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
