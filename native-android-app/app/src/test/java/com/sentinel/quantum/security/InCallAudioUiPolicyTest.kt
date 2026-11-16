package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InCallAudioUiPolicyTest {
    @Test fun labelsAreFrenchAndDeviceNamesAreBounded() {
        assertEquals("Écouteur", InCallAudioUiPolicy.label(InCallAudioUiPolicy.Kind.EARPIECE))
        assertEquals("Haut-parleur", InCallAudioUiPolicy.label(InCallAudioUiPolicy.Kind.SPEAKER))
        assertEquals(
            "Bluetooth · Galaxy Buds",
            InCallAudioUiPolicy.label(InCallAudioUiPolicy.Kind.BLUETOOTH, " Galaxy Buds ")
        )
        val long = InCallAudioUiPolicy.label(InCallAudioUiPolicy.Kind.BLUETOOTH, "x".repeat(200))
        assertTrue(long.length <= "Bluetooth · ".length + 80)
    }

    @Test fun presentationIsDeterministicAndDeduplicated() {
        val routes = InCallAudioUiPolicy.present(
            listOf(
                InCallAudioUiPolicy.Route("speaker", InCallAudioUiPolicy.Kind.SPEAKER),
                InCallAudioUiPolicy.Route("earpiece", InCallAudioUiPolicy.Kind.EARPIECE, selected = true),
                InCallAudioUiPolicy.Route("speaker", InCallAudioUiPolicy.Kind.SPEAKER)
            )
        )
        assertEquals(listOf("earpiece", "speaker"), routes.map { it.id })
        assertEquals(1, routes.count { it.selected })
        assertEquals("Écouteur", routes.first().label)
    }
}
