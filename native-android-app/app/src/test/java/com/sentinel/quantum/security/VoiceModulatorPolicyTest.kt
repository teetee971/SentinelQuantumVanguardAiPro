package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModulatorPolicyTest {
    @Test fun carrierCallsAreNeverUnlockedByPayment() {
        val result = VoiceModulatorPolicy.availability(
            VoiceModulatorPolicy.Input(
                transport = VoiceModulatorPolicy.Transport.CARRIER_PSTN,
                entitlement = VoiceModulatorPolicy.Entitlement.ACTIVE,
                engineValidated = true,
                microphonePermissionGranted = true
            )
        )
        assertEquals(VoiceModulatorPolicy.Availability.UNSUPPORTED_BY_ANDROID, result)
        assertFalse(
            VoiceModulatorPolicy.canTransformLiveCall(
                VoiceModulatorPolicy.Input(
                    VoiceModulatorPolicy.Transport.CARRIER_PSTN,
                    VoiceModulatorPolicy.Entitlement.ACTIVE,
                    engineValidated = true,
                    microphonePermissionGranted = true
                )
            )
        )
    }

    @Test fun managedVoipRequiresBothValidatedEngineAndEntitlement() {
        val base = VoiceModulatorPolicy.Input(
            VoiceModulatorPolicy.Transport.SENTINEL_MANAGED_VOIP,
            VoiceModulatorPolicy.Entitlement.ACTIVE,
            engineValidated = false,
            microphonePermissionGranted = true
        )
        assertEquals(
            VoiceModulatorPolicy.Availability.ENGINE_NOT_VALIDATED,
            VoiceModulatorPolicy.availability(base)
        )
        assertFalse(VoiceModulatorPolicy.canTransformLiveCall(base))

        val unpaid = base.copy(
            engineValidated = true,
            entitlement = VoiceModulatorPolicy.Entitlement.INACTIVE
        )
        assertEquals(
            VoiceModulatorPolicy.Availability.PURCHASE_REQUIRED,
            VoiceModulatorPolicy.availability(unpaid)
        )

        val ready = unpaid.copy(entitlement = VoiceModulatorPolicy.Entitlement.ACTIVE)
        assertTrue(VoiceModulatorPolicy.canTransformLiveCall(ready))
    }

    @Test fun localPreviewNeverRepresentsLiveCallReadiness() {
        val preview = VoiceModulatorPolicy.Input(
            VoiceModulatorPolicy.Transport.LOCAL_PREVIEW,
            VoiceModulatorPolicy.Entitlement.NOT_COMMERCIALIZED,
            engineValidated = false,
            microphonePermissionGranted = true
        )
        assertEquals(
            VoiceModulatorPolicy.Availability.PREVIEW_AVAILABLE,
            VoiceModulatorPolicy.availability(preview)
        )
        assertFalse(VoiceModulatorPolicy.canTransformLiveCall(preview))
    }
}
