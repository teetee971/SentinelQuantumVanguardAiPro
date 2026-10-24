package com.sentinel.quantum.security

import java.util.UUID

/**
 * Reserved GATT identifiers for a future authenticated transport.
 *
 * This contract alone MUST NOT be registered as writable GATT characteristics.
 * The existing marker remains read/write-disabled. Do not expose private data
 * until peer authentication, per-peer session isolation, and encryption exist.
 */
internal object OfflineMeshGattProtocolIds {
    val SERVICE: UUID = UUID.fromString("de939bda-5b41-4c5d-8bb7-58c24458a6e2")
    val MARKER: UUID = UUID.fromString("f63dd7df-68e2-4ffb-b949-a4d403a8ad67")
    val CLIENT_TO_SERVER: UUID = UUID.fromString("f10a1ce0-7062-4e31-8b87-f7fef44b4d01")
    val SERVER_TO_CLIENT: UUID = UUID.fromString("f10a1ce0-7062-4e31-8b87-f7fef44b4d02")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun isApplicationDataCharacteristic(uuid: UUID): Boolean =
        uuid == CLIENT_TO_SERVER || uuid == SERVER_TO_CLIENT

    fun isMarker(uuid: UUID): Boolean = uuid == MARKER

    /** No data is authorized by discovery or MTU readiness alone. */
    fun mayTransferPrivateData(linkReady: Boolean, cryptographicallyAuthenticated: Boolean): Boolean =
        linkReady && cryptographicallyAuthenticated
}
