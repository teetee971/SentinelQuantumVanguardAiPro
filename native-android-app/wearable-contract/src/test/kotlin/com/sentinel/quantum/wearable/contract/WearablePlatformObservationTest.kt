package com.sentinel.quantum.wearable.contract
import org.junit.Assert.assertEquals
import org.junit.Test

class WearablePlatformObservationTest {
    @Test fun platformObservationCarriesNoTrustState() {
        val value = WearablePlatformObservation("node-1", "Watch", WearableTransportKind.WEAR_OS, WearablePlatformLinkKind.COMPANION_ASSOCIATED)
        assertEquals(WearablePlatformLinkKind.COMPANION_ASSOCIATED, value.platformLink)
    }
    @Test(expected = IllegalArgumentException::class)
    fun blankIdentityHintIsRejected() {
        WearablePlatformObservation(" ", null, WearableTransportKind.WEAR_OS, WearablePlatformLinkKind.DISCOVERED)
    }
}
