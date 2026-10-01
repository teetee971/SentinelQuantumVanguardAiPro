package com.sentinel.quantum.voice

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
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
        return runCatching {
            val connectedRoom = LiveKit.connect(
                appContext = appContext,
                url = credentials.serverUrl,
                token = credentials.accessToken,
                overrides = voiceProcessor.liveKitOverrides()
            )
            room = connectedRoom
            state = State.CONNECTED

            val microphonePublished =
                connectedRoom.localParticipant.setMicrophoneEnabled(true)
            if (!microphonePublished) {
                connectedRoom.disconnect()
                connectedRoom.release()
                room = null
                state = State.FAILED
                error("LiveKit microphone publication failed")
            }
            state = State.ACTIVE_MIC
        }.onFailure {
            room?.disconnect()
            room?.release()
            room = null
            state = State.FAILED
        }
    }

    suspend fun disconnect() = mutex.withLock {
        room?.localParticipant?.setMicrophoneEnabled(false)
        room?.disconnect()
        room?.release()
        room = null
        state = State.DISCONNECTED
    }
}
