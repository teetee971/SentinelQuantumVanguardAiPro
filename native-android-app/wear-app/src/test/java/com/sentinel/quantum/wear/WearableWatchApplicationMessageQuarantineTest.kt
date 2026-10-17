package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableWatchApplicationMessageQuarantineTest {
    @Test fun applicationMessageCannotReachHandlerUntilAuthenticatedConsumerExists() {
        var invoked = false
        val handler = WearableSecureFrameHandler { _, _, _ -> invoked = true }
        WearableSecureTransportInbox.install(handler)
        try {
            assertFalse(
                WearableSecureTransportInbox.dispatch(
                    "phone-node",
                    WearableProtocolV2.MESSAGE_PATH,
                    WearableProtocolV2.Frame(byteArrayOf(1, 2, 3))
                )
            )
            assertFalse(invoked)
        } finally {
            WearableSecureTransportInbox.clear(handler)
        }
    }

    @Test fun quarantineDoesNotBlockAuthenticatedLivenessTransportRouting() {
        var deliveredPath: String? = null
        val handler = WearableSecureFrameHandler { _, path, _ -> deliveredPath = path }
        WearableSecureTransportInbox.install(handler)
        try {
            assertTrue(
                WearableSecureTransportInbox.dispatch(
                    "phone-node",
                    WearableProtocolV2.LIVENESS_PATH,
                    WearableProtocolV2.Frame(byteArrayOf(4, 5, 6))
                )
            )
            org.junit.Assert.assertEquals(WearableProtocolV2.LIVENESS_PATH, deliveredPath)
        } finally {
            WearableSecureTransportInbox.clear(handler)
        }
    }
}
