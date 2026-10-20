package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PairedBluetoothInventoryTest {
    @Test
    fun galaxyWatch6IsRecognizedAsProbableWearable() {
        assertEquals("Montre probable", PairedBluetoothInventory.classifyDisplayName("Galaxy Watch6 (ADND)"))
        assertEquals("Montre probable", PairedBluetoothInventory.classifyDisplayName("Watch6 de Thierry"))
    }

    @Test
    fun recognizesFrenchAndFitNames() {
        assertEquals("Montre probable", PairedBluetoothInventory.classifyDisplayName("Montre connectée"))
        assertEquals("Montre probable", PairedBluetoothInventory.classifyDisplayName("Galaxy Fit3"))
    }

    @Test
    fun doesNotClaimSpeakersOrUnknownDevicesAreWatches() {
        assertEquals("Bluetooth", PairedBluetoothInventory.classifyDisplayName("JBL Boombox 2"))
        assertEquals("Bluetooth", PairedBluetoothInventory.classifyDisplayName("Appareil associé (nom indisponible)"))
    }
}
