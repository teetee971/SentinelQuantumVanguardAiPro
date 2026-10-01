package com.sentinel.quantum.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAddonPolicyTest {
    @Test
    fun liveTransformationIsARequiredIntegratedProductCapability() {
        val state = VoiceAddonPolicy.currentState()

        assertTrue(state.previewAvailable)
        assertTrue(state.liveTransformRequired)
        assertTrue(state.liveTransformEngineIntegrated)
        assertTrue(state.webRtcClientIntegrated)
        assertFalse(state.runtimeCallFlowIntegrated)
        assertFalse(state.paidCheckoutAllowed)
        assertTrue(
            state.liveCallPath ==
                VoiceAddonPolicy.LiveCallPath.SENTINEL_WEBRTC_CLIENT_INTEGRATED_SERVICE_PENDING
        )
    }

    @Test
    fun paidCheckoutRequiresTheCompleteSentinelOwnedCallPath() {
        assertFalse(VoiceAddonPolicy.mayOfferPaidCheckout(false, true, true, true, true, true, true))
        assertFalse(VoiceAddonPolicy.mayOfferPaidCheckout(true, false, true, true, true, true, true))
        assertFalse(VoiceAddonPolicy.mayOfferPaidCheckout(true, true, false, true, true, true, true))
        assertFalse(VoiceAddonPolicy.mayOfferPaidCheckout(true, true, true, false, true, true, true))
        assertFalse(VoiceAddonPolicy.mayOfferPaidCheckout(true, true, true, true, false, true, true))
        assertFalse(VoiceAddonPolicy.mayOfferPaidCheckout(true, true, true, true, true, false, true))
        assertFalse(VoiceAddonPolicy.mayOfferPaidCheckout(true, true, true, true, true, true, false))
        assertTrue(VoiceAddonPolicy.mayOfferPaidCheckout(true, true, true, true, true, true, true))
    }}

