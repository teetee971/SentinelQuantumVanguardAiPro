package com.sentinel.quantum.talkiewalkie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushToTalkGuardTest {
    private val guard = PushToTalkGuard()
    private val nowMs = 1_000L
    private val sessionId = "session-a"

    @Test
    fun validSnapshotAllowsTransmit() {
        val decision = guard.canTransmit(validSnapshot())

        assertTrue(decision.allowed)
        assertEquals(null, decision.reason)
    }

    @Test
    fun expiredFloorDeniesTransmit() {
        val decision = guard.canTransmit(
            validSnapshot().copy(
                floorLease = FloorLease("floor", sessionId, expiresAtMs = nowMs)
            )
        )

        assertFalse(decision.allowed)
        assertEquals(GuardDenialReason.INVALID_FLOOR, decision.reason)
    }

    @Test
    fun wrongFloorHolderDeniesTransmit() {
        val decision = guard.canTransmit(
            validSnapshot().copy(
                floorLease = FloorLease("floor", "other-session", expiresAtMs = nowMs + 5_000L)
            )
        )

        assertFalse(decision.allowed)
        assertEquals(GuardDenialReason.INVALID_FLOOR, decision.reason)
    }

    @Test
    fun expiredTemporaryChannelDeniesTransmit() {
        val decision = guard.canTransmit(
            validSnapshot().copy(
                channelPolicy = ChannelPolicy(
                    expiresAtMs = nowMs,
                    canTalk = true,
                    secureReplayAllowed = false
                )
            )
        )

        assertFalse(decision.allowed)
        assertEquals(GuardDenialReason.CHANNEL_EXPIRED, decision.reason)
    }

    @Test
    fun missingMicrophonePermissionDeniesTransmit() {
        val decision = guard.canTransmit(validSnapshot().copy(microphonePermissionGranted = false))

        assertFalse(decision.allowed)
        assertEquals(GuardDenialReason.MICROPHONE_PERMISSION_MISSING, decision.reason)
    }

    @Test
    fun disconnectedTransportDeniesTransmit() {
        val decision = guard.canTransmit(validSnapshot().copy(transportConnected = false))

        assertFalse(decision.allowed)
        assertEquals(GuardDenialReason.TRANSPORT_DISCONNECTED, decision.reason)
    }

    @Test
    fun reconnectingStateDeniesTransmit() {
        val decision = guard.canTransmit(validSnapshot().copy(state = TalkieWalkieState.RECONNECTING))

        assertFalse(decision.allowed)
        assertEquals(GuardDenialReason.INVALID_STATE, decision.reason)
    }

    @Test
    fun missingAudioFocusDeniesTransmit() {
        val decision = guard.canTransmit(validSnapshot().copy(audioFocusGranted = false))

        assertFalse(decision.allowed)
        assertEquals(GuardDenialReason.AUDIO_FOCUS_MISSING, decision.reason)
    }

    @Test
    fun channelExpiringBetweenListeningAndFloorRequestFailsClosed() {
        val listening = validSnapshot().copy(
            state = TalkieWalkieState.LISTENING,
            channelPolicy = ChannelPolicy(
                expiresAtMs = nowMs + 1L,
                canTalk = true,
                secureReplayAllowed = false
            )
        )
        assertFalse(guard.canTransmit(listening).allowed)

        val requestingAfterExpiry = listening.copy(
            nowMs = nowMs + 1L,
            state = TalkieWalkieState.REQUESTING_FLOOR
        )
        val decision = guard.canTransmit(requestingAfterExpiry)

        assertFalse(decision.allowed)
        assertEquals(GuardDenialReason.CHANNEL_EXPIRED, decision.reason)
    }

    private fun validSnapshot() = GuardSnapshot(
        nowMs = nowMs,
        sessionId = sessionId,
        floorLease = FloorLease("floor", sessionId, expiresAtMs = nowMs + 5_000L),
        channelPolicy = ChannelPolicy(
            expiresAtMs = nowMs + 10_000L,
            canTalk = true,
            secureReplayAllowed = false
        ),
        microphonePermissionGranted = true,
        transportConnected = true,
        audioFocusGranted = true,
        state = TalkieWalkieState.REQUESTING_FLOOR
    )
}
