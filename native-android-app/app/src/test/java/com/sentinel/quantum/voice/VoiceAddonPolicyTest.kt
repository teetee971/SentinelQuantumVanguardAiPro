package com.sentinel.quantum.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAddonPolicyTest {
    @Test
    fun carrierCallModulation_isNeverSoldByDefault() {
        val state = VoiceAddonPolicy.currentState()

        assertTrue(state.previewAvailable)
        assertFalse(state.paidCheckoutAllowed)
        assertTrue(state.liveCallPath == VoiceAddonPolicy.LiveCallPath.CARRIER_SIM_UNSUPPORTED)
    }

    @Test
    fun paidCheckout_requiresOwnedVoipAndAllValidationGates() {
        assertFalse(VoiceAddonPolicy.mayOfferPaidCheckout(false, true, true))
        assertFalse(VoiceAddonPolicy.mayOfferPaidCheckout(true, false, true))
        assertFalse(VoiceAddonPolicy.mayOfferPaidCheckout(true, true, false))
        assertTrue(VoiceAddonPolicy.mayOfferPaidCheckout(true, true, true))
    }
}
