package com.sentinel.quantum.talkiewalkie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentReceiveBufferTest {
    @Test
    fun evictsRemoteFramesOlderThanTwentySeconds() {
        val buffer = RecentReceiveBuffer(maxDurationMs = 20_000L)
        buffer.updateContext(channelId = "channel-a", secureReplayAllowed = true)

        assertTrue(buffer.append(remoteFrame(atMs = 0L, marker = 1)))
        assertTrue(buffer.append(remoteFrame(atMs = 10_000L, marker = 2)))
        assertTrue(buffer.append(remoteFrame(atMs = 21_001L, marker = 3)))

        assertEquals(
            listOf(2, 3),
            buffer.snapshot(nowMs = 21_001L).map { it.payload.first().toInt() }
        )
    }

    @Test
    fun localMicrophoneFramesAreNeverAccepted() {
        val buffer = RecentReceiveBuffer()
        buffer.updateContext(channelId = "channel-a", secureReplayAllowed = true)

        val accepted = buffer.append(
            RecentReceiveFrame(
                source = RecentReceiveFrame.Source.LOCAL_MIC,
                channelId = "channel-a",
                receivedAtMs = 1_000L,
                payload = byteArrayOf(7)
            )
        )

        assertFalse(accepted)
        assertTrue(buffer.snapshot(nowMs = 1_000L).isEmpty())
    }

    @Test
    fun channelOrSecurityPolicyChangeClearsMemory() {
        val buffer = RecentReceiveBuffer()
        buffer.updateContext(channelId = "channel-a", secureReplayAllowed = true)
        assertTrue(buffer.append(remoteFrame(atMs = 1_000L, marker = 1)))

        buffer.updateContext(channelId = "channel-b", secureReplayAllowed = true)
        assertTrue(buffer.snapshot(nowMs = 1_000L).isEmpty())

        assertTrue(buffer.append(remoteFrame(channelId = "channel-b", atMs = 2_000L, marker = 2)))
        buffer.updateContext(channelId = "channel-b", secureReplayAllowed = false)
        assertTrue(buffer.snapshot(nowMs = 2_000L).isEmpty())
        assertFalse(buffer.append(remoteFrame(channelId = "channel-b", atMs = 2_100L, marker = 3)))
    }

    private fun remoteFrame(
        channelId: String = "channel-a",
        atMs: Long,
        marker: Int
    ) = RecentReceiveFrame(
        source = RecentReceiveFrame.Source.REMOTE,
        channelId = channelId,
        receivedAtMs = atMs,
        payload = byteArrayOf(marker.toByte())
    )
}
