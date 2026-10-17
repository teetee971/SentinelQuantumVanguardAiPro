package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableSecureTransportInboxTest {
    @Test fun missingHandlerFailsClosed() {
        WearableSecureTransportInbox.clear()
        assertFalse(
            WearableSecureTransportInbox.dispatch(
                "node-1",
                WearableProtocolV2.LIVENESS_PATH,
                WearableProtocolV2.Frame(byteArrayOf(1))
            )
        )
    }

    @Test fun handlerFailureCannotEscapeTransportBoundary() {
        WearableSecureTransportInbox.install(
            WearableSecureFrameHandler { _, _, _ -> error("simulated watch handler failure") }
        )
        try {
            assertFalse(
                WearableSecureTransportInbox.dispatch(
                    "node-1",
                    WearableProtocolV2.LIVENESS_PATH,
                    WearableProtocolV2.Frame(byteArrayOf(1))
                )
            )
        } finally {
            WearableSecureTransportInbox.clear()
        }
    }

    @Test fun successfulHandlerIsReportedAsDispatched() {
        var called = false
        WearableSecureTransportInbox.install(
            WearableSecureFrameHandler { _, _, _ -> called = true }
        )
        try {
            assertTrue(
                WearableSecureTransportInbox.dispatch(
                    "node-1",
                    WearableProtocolV2.MESSAGE_PATH,
                    WearableProtocolV2.Frame(byteArrayOf(1))
                )
            )
            assertTrue(called)
        } finally {
            WearableSecureTransportInbox.clear()
        }
    }

    @Test fun staleOwnerCannotClearNewerHandler() {
        var newerCalled = false
        val older = WearableSecureFrameHandler { _, _, _ -> Unit }
        val newer = WearableSecureFrameHandler { _, _, _ -> newerCalled = true }
        WearableSecureTransportInbox.install(older)
        WearableSecureTransportInbox.install(newer)

        assertFalse(WearableSecureTransportInbox.clear(older))
        assertTrue(
            WearableSecureTransportInbox.dispatch(
                "node-1",
                WearableProtocolV2.MESSAGE_PATH,
                WearableProtocolV2.Frame(byteArrayOf(1))
            )
        )
        assertTrue(newerCalled)
        assertTrue(WearableSecureTransportInbox.clear(newer))
    }
}
