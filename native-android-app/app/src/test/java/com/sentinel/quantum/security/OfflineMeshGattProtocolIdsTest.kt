package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMeshGattProtocolIdsTest {
    @Test fun transportCharacteristicsNeverReuseLegacyMarker() {
        val ids = OfflineMeshGattProtocolIds
        assertNotEquals(ids.MARKER, ids.CLIENT_TO_SERVER)
        assertNotEquals(ids.MARKER, ids.SERVER_TO_CLIENT)
        assertNotEquals(ids.CLIENT_TO_SERVER, ids.SERVER_TO_CLIENT)
        assertNotEquals(ids.CCCD, ids.CLIENT_TO_SERVER)
        assertNotEquals(ids.CCCD, ids.SERVER_TO_CLIENT)
        assertEquals(ids.MARKER, java.util.UUID.fromString("f63dd7df-68e2-4ffb-b949-a4d403a8ad67"))
        assertTrue(ids.isMarker(ids.MARKER))
        assertFalse(ids.isApplicationDataCharacteristic(ids.MARKER))
        assertTrue(ids.isApplicationDataCharacteristic(ids.CLIENT_TO_SERVER))
        assertTrue(ids.isApplicationDataCharacteristic(ids.SERVER_TO_CLIENT))
    }

    @Test fun discoveryAndMtuAloneNeverAuthorizePrivatePayload() {
        val ids = OfflineMeshGattProtocolIds
        assertFalse(ids.mayTransferPrivateData(false, false))
        assertFalse(ids.mayTransferPrivateData(true, false))
        assertFalse(ids.mayTransferPrivateData(false, true))
        assertTrue(ids.mayTransferPrivateData(true, true))
    }
}
