package com.sentinel.quantum.talkiewalkie

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveKitTalkieWalkieTransportContractTest {
    private val credentials = TalkieWalkieTransport.Credentials(
        serverUrl = "wss://voice.example.test",
        accessToken = "x".repeat(64)
    )

    @Test
    fun connectSucceedsWithMicrophoneMuted() = runBlocking {
        val room = FakePttRoom()
        val transport = LiveKitTalkieWalkieTransport(
            permissionGranted = { true },
            roomFactory = { room }
        )

        val result = transport.connect(credentials)

        assertTrue(result.isSuccess)
        assertEquals(listOf("connect", "mic:false"), room.events)
        assertFalse(transport.isMicrophoneEnabled())
        assertEquals(TalkieWalkieTransport.ConnectionState.CONNECTED, transport.connectionState())
    }

    @Test
    fun insecureEndpointFailsBeforeRoomCreation() = runBlocking {
        var roomCreated = false
        val transport = LiveKitTalkieWalkieTransport(
            permissionGranted = { true },
            roomFactory = {
                roomCreated = true
                FakePttRoom()
            }
        )

        val result = transport.connect(
            credentials.copy(serverUrl = "ws://voice.example.test")
        )

        assertTrue(result.isFailure)
        assertFalse(roomCreated)
        assertFalse(transport.isMicrophoneEnabled())
    }

    @Test
    fun disconnectMutesBeforeDisconnectAndRelease() = runBlocking {
        val room = FakePttRoom()
        val transport = LiveKitTalkieWalkieTransport(
            permissionGranted = { true },
            roomFactory = { room }
        )
        assertTrue(transport.connect(credentials).isSuccess)
        assertTrue(transport.setMicrophoneEnabled(true).isSuccess)

        transport.disconnect()

        assertEquals(
            listOf("connect", "mic:false", "mic:true", "mic:false", "disconnect", "release"),
            room.events
        )
        assertFalse(transport.isMicrophoneEnabled())
        assertEquals(TalkieWalkieTransport.ConnectionState.DISCONNECTED, transport.connectionState())
    }

    @Test
    fun reconnectCallbackForcesMutedStateAndNeverRestoresPreviousTransmit() = runBlocking {
        val room = FakePttRoom()
        val transport = LiveKitTalkieWalkieTransport(
            permissionGranted = { true },
            roomFactory = { room }
        )
        assertTrue(transport.connect(credentials).isSuccess)
        assertTrue(transport.setMicrophoneEnabled(true).isSuccess)

        room.simulateReconnected()

        assertFalse(transport.isMicrophoneEnabled())
        assertEquals("mic:false", room.events.last())
        assertEquals(TalkieWalkieTransport.ConnectionState.CONNECTED, transport.connectionState())
    }

    private class FakePttRoom : LiveKitTalkieWalkieRoom {
        val events = mutableListOf<String>()
        private var reconnectedHandler: (suspend () -> Unit)? = null

        override suspend fun connect(url: String, token: String) {
            events += "connect"
        }

        override suspend fun setMicrophoneEnabled(enabled: Boolean): Boolean {
            events += "mic:$enabled"
            return true
        }

        override fun setReconnectedHandler(handler: suspend () -> Unit) {
            reconnectedHandler = handler
        }

        override fun disconnect() {
            events += "disconnect"
        }

        override fun release() {
            events += "release"
        }

        suspend fun simulateReconnected() {
            reconnectedHandler?.invoke()
        }
    }
}
