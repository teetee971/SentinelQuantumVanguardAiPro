package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneWearableTransportValidationTest {
    @Test fun unknownAndWrongDirectionFramesAreRejectedBeforeDispatch() {
        var calls = 0
        WearableSecureTransportInbox.install(WearableSecureFrameHandler { _, _, _ -> calls++ })
        val frame = WearableProtocolV2.Frame(byteArrayOf(1))
        assertFalse(WearableSecureTransportInbox.dispatch("node", "/sentinel/v2/secure/other", frame))
        assertFalse(WearableSecureTransportInbox.dispatch("node", WearableProtocolV2.CHALLENGE_PATH, frame))
        assertFalse(WearableSecureTransportInbox.dispatch("", WearableProtocolV2.LIVENESS_PATH, frame))
        assertFalse(WearableSecureTransportInbox.dispatch("node", WearableProtocolV2.LIVENESS_PATH, WearableProtocolV2.Frame(byteArrayOf())))
        assertTrue(WearableSecureTransportInbox.dispatch("node", WearableProtocolV2.LIVENESS_PATH, frame))
        org.junit.Assert.assertEquals(1, calls)
        WearableSecureTransportInbox.clear()
    }
}
