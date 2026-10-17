package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import org.junit.Assert.assertFalse
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
}
