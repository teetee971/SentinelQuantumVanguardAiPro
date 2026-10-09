package com.sentinel.quantum.talkiewalkie

import java.util.ArrayDeque

data class RecentReceiveFrame(
    val source: Source,
    val channelId: String,
    val receivedAtMs: Long,
    val payload: ByteArray
) {
    enum class Source {
        REMOTE,
        LOCAL_MIC
    }
}

class RecentReceiveBuffer(
    private val maxDurationMs: Long = 20_000L
) {
    init {
        require(maxDurationMs > 0L) { "maxDurationMs must be positive" }
    }

    private val frames = ArrayDeque<RecentReceiveFrame>()
    private var activeChannelId: String? = null
    private var secureReplayAllowed: Boolean = false

    @Synchronized
    fun updateContext(channelId: String, secureReplayAllowed: Boolean) {
        val contextChanged =
            activeChannelId != channelId || this.secureReplayAllowed != secureReplayAllowed
        if (contextChanged) {
            clearInternal()
        }
        activeChannelId = channelId
        this.secureReplayAllowed = secureReplayAllowed
    }

    @Synchronized
    fun append(frame: RecentReceiveFrame): Boolean {
        if (!secureReplayAllowed) return false
        if (frame.source != RecentReceiveFrame.Source.REMOTE) return false
        if (frame.channelId != activeChannelId) return false
        if (frame.receivedAtMs < 0L) return false

        frames.addLast(frame.safeCopy())
        evictExpired(frame.receivedAtMs)
        return true
    }

    @Synchronized
    fun snapshot(nowMs: Long): List<RecentReceiveFrame> {
        evictExpired(nowMs)
        return frames.map { it.safeCopy() }
    }

    @Synchronized
    fun clear() {
        clearInternal()
    }

    private fun evictExpired(nowMs: Long) {
        val cutoffMs = nowMs - maxDurationMs
        while (frames.isNotEmpty() && frames.peekFirst().receivedAtMs < cutoffMs) {
            frames.removeFirst()
        }
    }

    private fun clearInternal() {
        frames.clear()
    }

    private fun RecentReceiveFrame.safeCopy(): RecentReceiveFrame =
        copy(payload = payload.copyOf())
}
