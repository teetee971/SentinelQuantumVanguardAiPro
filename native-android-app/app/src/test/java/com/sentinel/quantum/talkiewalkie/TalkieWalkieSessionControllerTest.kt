package com.sentinel.quantum.talkiewalkie

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkieWalkieSessionControllerTest {
    private val nowMs = 1_000L
    private val sessionId = "session-a"
    private val channelPolicy = ChannelPolicy(
        expiresAtMs = nowMs + 60_000L,
        canTalk = true,
        secureReplayAllowed = false
    )

    @Test
    fun pressRequestsFloorBeforeUnmuteAndOnlyThenTransmits() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = FakeTransport(calls)
        val floor = FakeFloorController(calls, validLease())
        val watchdog = FakeWatchdog(calls)
        val controller = controller(transport, floor, watchdog)

        val result = controller.pressToTalk()

        assertTrue(result.isSuccess)
        assertEquals(listOf("floor:request", "mic:true", "watchdog:arm"), calls)
        assertEquals(TalkieWalkieState.TRANSMITTING, controller.snapshot().state)
        assertTrue(transport.microphoneEnabled)
    }

    @Test
    fun releaseMutesBeforeStoppingFloorRenewalAndReturningToListening() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = FakeTransport(calls)
        val floor = FakeFloorController(calls, validLease())
        val watchdog = FakeWatchdog(calls)
        val controller = controller(transport, floor, watchdog)
        assertTrue(controller.pressToTalk().isSuccess)
        calls.clear()

        controller.releaseToTalk()

        assertEquals(
            listOf("mic:false", "watchdog:disarm", "floor:stop-renewal", "floor:release"),
            calls
        )
        assertFalse(transport.microphoneEnabled)
        assertEquals(TalkieWalkieState.LISTENING, controller.snapshot().state)
    }

    @Test
    fun transportLossWhileTransmittingForceMutesExactlyOnce() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = FakeTransport(calls)
        val floor = FakeFloorController(calls, validLease())
        val watchdog = FakeWatchdog(calls)
        val controller = controller(transport, floor, watchdog)
        assertTrue(controller.pressToTalk().isSuccess)
        calls.clear()

        controller.onTransportLost()
        controller.onTransportLost()

        assertEquals(1, calls.count { it == "mic:false" })
        assertFalse(transport.microphoneEnabled)
        assertEquals(TalkieWalkieState.RECONNECTING, controller.snapshot().state)
    }

    @Test
    fun triggerLossWhileTransmittingForceMutesExactlyOnce() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = FakeTransport(calls)
        val floor = FakeFloorController(calls, validLease())
        val watchdog = FakeWatchdog(calls)
        val controller = controller(transport, floor, watchdog)
        assertTrue(controller.pressToTalk().isSuccess)
        calls.clear()

        controller.onTriggerLost()
        controller.onTriggerLost()

        assertEquals(1, calls.count { it == "mic:false" })
        assertFalse(transport.microphoneEnabled)
        assertEquals(TalkieWalkieState.LISTENING, controller.snapshot().state)
    }

    @Test
    fun reconnectReturnsToListeningWithoutRestoringTransmit() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = FakeTransport(calls)
        val floor = FakeFloorController(calls, validLease())
        val watchdog = FakeWatchdog(calls)
        val controller = controller(transport, floor, watchdog)
        assertTrue(controller.pressToTalk().isSuccess)
        controller.onTransportLost()
        calls.clear()

        controller.onTransportRestored()

        assertEquals(TalkieWalkieState.LISTENING, controller.snapshot().state)
        assertFalse(transport.microphoneEnabled)
        assertFalse(calls.contains("mic:true"))
    }

    @Test
    fun failedUnmuteReleasesFloorAndNeverClaimsTransmitting() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = FakeTransport(calls, failEnable = true)
        val floor = FakeFloorController(calls, validLease())
        val watchdog = FakeWatchdog(calls)
        val controller = controller(transport, floor, watchdog)

        val result = controller.pressToTalk()

        assertTrue(result.isFailure)
        assertFalse(transport.microphoneEnabled)
        assertEquals(TalkieWalkieState.LISTENING, controller.snapshot().state)
        assertTrue(calls.indexOf("mic:true") < calls.indexOf("floor:release"))
        assertFalse(calls.contains("watchdog:arm"))
    }

    private fun controller(
        transport: FakeTransport,
        floor: FakeFloorController,
        watchdog: FakeWatchdog
    ) = TalkieWalkieSessionController(
        transport = transport,
        floorController = floor,
        watchdog = watchdog,
        sessionId = sessionId,
        nowMs = { nowMs },
        channelPolicy = { channelPolicy },
        microphonePermissionGranted = { true },
        audioFocusGranted = { true },
        initialState = TalkieWalkieState.LISTENING
    )

    private fun validLease() = FloorLease(
        leaseId = "floor-a",
        holderSessionId = sessionId,
        expiresAtMs = nowMs + 5_000L
    )

    private class FakeTransport(
        private val calls: MutableList<String>,
        private val failEnable: Boolean = false
    ) : TalkieWalkieTransport {
        var microphoneEnabled = false
        var state = TalkieWalkieTransport.ConnectionState.CONNECTED

        override suspend fun connect(credentials: TalkieWalkieTransport.Credentials): Result<Unit> =
            Result.success(Unit)

        override suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit> {
            calls += "mic:$enabled"
            if (enabled && failEnable) {
                microphoneEnabled = false
                return Result.failure(IllegalStateException("synthetic unmute failure"))
            }
            microphoneEnabled = enabled
            return Result.success(Unit)
        }

        override suspend fun disconnect() {
            microphoneEnabled = false
            state = TalkieWalkieTransport.ConnectionState.DISCONNECTED
        }

        override fun isMicrophoneEnabled(): Boolean = microphoneEnabled

        override fun connectionState(): TalkieWalkieTransport.ConnectionState = state
    }

    private class FakeFloorController(
        private val calls: MutableList<String>,
        private val lease: FloorLease
    ) : FloorController {
        override suspend fun requestFloor(sessionId: String, nowMs: Long): Result<FloorLease> {
            calls += "floor:request"
            return Result.success(lease)
        }

        override fun startRenewal(lease: FloorLease) {
            calls += "floor:start-renewal"
        }

        override fun stopRenewal() {
            calls += "floor:stop-renewal"
        }

        override suspend fun releaseFloor(lease: FloorLease) {
            calls += "floor:release"
        }
    }

    private class FakeWatchdog(
        private val calls: MutableList<String>
    ) : PttWatchdog {
        override fun arm() {
            calls += "watchdog:arm"
        }

        override fun disarm() {
            calls += "watchdog:disarm"
        }
    }
}
