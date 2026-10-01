package com.sentinel.quantum.voice

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI

/**
 * Concrete WebRTC transport boundary for Sentinel voice calls.
 *
 * Credentials are ephemeral inputs supplied by a trusted backend. They are never persisted
 * or logged here. The transport only accepts TLS WebSocket endpoints and does not enable the
 * microphone unless the LiveKit room connection succeeded.
 */
class SentinelLiveKitCallTransport(
    context: Context,
    private val voiceProcessor: LiveKitVoiceAudioProcessor
) {
    data class Credentials(
        val serverUrl: String,
        val accessToken: String
    ) {
        fun validate(): Boolean {
            val uri = runCatching { URI(serverUrl) }.getOrNull() ?: return false
            return uri.scheme.equals("wss", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                accessToken.length in 32..16_384 &&
                accessToken.none(Char::isWhitespace)
        }
    }

    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ACTIVE_MIC,
        FAILED
    }

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile
    private var state: State = State.DISCONNECTED

    private var room: Room? = null

    fun state(): State = state

    fun configureVoice(
        enabled: Boolean,
        effect: VoiceAddonPolicy.Effect
    ) {
        voiceProcessor.configure(enabled, effect)
    }

    suspend fun connect(credentials: Credentials): Result<Unit> = mutex.withLock {
        if (!credentials.validate()) {
            state = State.FAILED
            return Result.failure(IllegalArgumentException("Invalid secure LiveKit credentials"))
        }
        if (room != null) {
            return Result.failure(IllegalStateException("LiveKit room already connected"))
        }

        state = State.CONNECTING
        var pendingRoom: Room? = null
        try {
            val connectedRoom = LiveKit.create(
                appContext = appContext,
                overrides = voiceProcessor.liveKitOverrides()
            )
            pendingRoom = connectedRoom
            connectedRoom.connect(
                url = credentials.serverUrl,
                token = credentials.accessToken
            )
            room = connectedRoom
            pendingRoom = null
            state = State.CONNECTED

            val microphonePublished =
                connectedRoom.localParticipant.setMicrophoneEnabled(true)
            if (!microphonePublished) {
                error("LiveKit microphone publication failed")
            }
            state = State.ACTIVE_MIC
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            pendingRoom?.disconnect()
            pendingRoom?.release()
            room?.disconnect()
            room?.release()
            room = null
            state = State.DISCONNECTED
            throw cancelled
        } catch (failure: Exception) {
            pendingRoom?.disconnect()
            pendingRoom?.release()
            room?.disconnect()
            room?.release()
            room = null
            state = State.FAILED
            Result.failure(failure)
        }
    }

    suspend fun disconnect() = mutex.withLock {
        val connectedRoom = room
        room = null
        try {
            connectedRoom?.localParticipant?.setMicrophoneEnabled(false)
        } finally {
            connectedRoom?.disconnect()
            connectedRoom?.release()
            state = State.DISCONNECTED
        }
    }
}
