package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CallRecorderCapabilityTest {
    @Test fun missingPermissionFailsClosed() {
        val r = CallRecorderCapability.evaluate(CallRecorderCapability.Inputs(true, false, true, true, true))
        assertEquals(CallRecorderCapability.CaptureLevel.UNAVAILABLE, r.level)
        assertFalse(r.canStartRecording)
        assertFalse(r.canClaimTwoPartyAudio)
    }

    @Test fun microphoneOnlyNeverClaimsRemoteParty() {
        val r = CallRecorderCapability.evaluate(CallRecorderCapability.Inputs(true, true, false, false, false))
        assertEquals(CallRecorderCapability.CaptureLevel.MICROPHONE_ONLY, r.level)
        assertTrue(r.canStartRecording)
        assertFalse(r.canClaimTwoPartyAudio)
    }

    @Test fun deviceStrategyWithoutProbeDoesNotOverclaim() {
        val r = CallRecorderCapability.evaluate(CallRecorderCapability.Inputs(true, true, true, true, false))
        assertEquals(CallRecorderCapability.CaptureLevel.DEVICE_DEPENDENT_CALL_AUDIO, r.level)
        assertFalse(r.canClaimTwoPartyAudio)
    }

    @Test fun twoPartyLabelRequiresSuccessfulProbe() {
        val r = CallRecorderCapability.evaluate(CallRecorderCapability.Inputs(true, true, true, true, true))
        assertEquals(CallRecorderCapability.CaptureLevel.VERIFIED_TWO_PARTY, r.level)
        assertTrue(r.canStartRecording)
        assertTrue(r.canClaimTwoPartyAudio)
    }
}
