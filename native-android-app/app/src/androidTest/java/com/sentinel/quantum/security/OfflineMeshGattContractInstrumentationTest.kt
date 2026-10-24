package com.sentinel.quantum.security

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on Android and verifies the characteristic/CCCD contract without
 * pretending that an emulator is a physical two-device BLE qualification.
 */
@RunWith(AndroidJUnit4::class)
class OfflineMeshGattContractInstrumentationTest {
    @Test
    fun serviceContractSeparatesWriteAndNotifyDirections() {
        val service = BluetoothGattService(
            OfflineMeshGattProtocolIds.SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val marker = BluetoothGattCharacteristic(OfflineMeshGattProtocolIds.MARKER, 0, 0)
        val write = BluetoothGattCharacteristic(
            OfflineMeshGattProtocolIds.CLIENT_TO_SERVER,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val notify = BluetoothGattCharacteristic(
            OfflineMeshGattProtocolIds.SERVER_TO_CLIENT,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        notify.addDescriptor(
            BluetoothGattDescriptor(
                OfflineMeshGattProtocolIds.CCCD,
                BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
        service.addCharacteristic(marker)
        service.addCharacteristic(write)
        service.addCharacteristic(notify)

        assertEquals(0, marker.properties)
        assertEquals(0, marker.permissions)
        assertTrue(write.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
        assertTrue(write.permissions and BluetoothGattCharacteristic.PERMISSION_WRITE != 0)
        assertTrue(notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
        assertNotNull(notify.getDescriptor(OfflineMeshGattProtocolIds.CCCD))
        assertEquals(3, service.characteristics.size)
        assertEquals(
            setOf(
                OfflineMeshGattProtocolIds.MARKER,
                OfflineMeshGattProtocolIds.CLIENT_TO_SERVER,
                OfflineMeshGattProtocolIds.SERVER_TO_CLIENT
            ),
            service.characteristics.map { it.uuid }.toSet()
        )
    }

    @Test
    fun connectionAndCccdAreNotPrivateDataAuthorization() {
        val peers = OfflineMeshGattPeerSessions()
        val token = peers.connect("instrumented-peer")!!
        assertTrue(peers.setNotifications(token, true))
        assertTrue(peers.snapshot(token)!!.notificationsEnabled)
        assertTrue(!peers.canTransferPrivateData(token))
    }
}
