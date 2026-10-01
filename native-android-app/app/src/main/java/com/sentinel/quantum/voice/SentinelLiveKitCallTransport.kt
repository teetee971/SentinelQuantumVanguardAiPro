package com.sentinel.quantum.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
class SentinelLiveKitCallTransport internal constructor(
    private val voiceProcessor: LiveKitVoiceAudioProcessor,
    private val permissionGranted: () -> Boolean,
    private val roomFactory: () -> Room
) {
    constructor(
        context: Context,
        voiceProcessor: LiveKitVoiceAudioProcessor
    ) : this(
        voiceProcessor = voiceProcessor,
        permissionGranted = microphonePermissionCheck(context.applicationContext),
        roomFactory = liveKitRoomFactory(context.applicationContext, voiceProcessor)
    )

    companion object {
        private fun microphonePermissionCheck(appContext: Context): () -> Boolean = {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        private fun liveKitRoomFactory(
            appContext: Context,
            voiceProcessor: LiveKitVoiceAudioProcessor
        ): () -> Room = {
            LiveKit.create(
                appContext = appContext,
                overrides = voiceProcessor.liveKitOverrides()
            )
        }
    }
    data class Credentials(
        val serverUrl: String,
        val accessToken: String
    ) {
        fun validate(): Boolean {
            val uri = runCatching { URI(serverUrl) }.getOrNull() ?: return false
            return uri.scheme.equals("wss", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
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

    private val mutex = Mutex()

    @Volatile
    private var state: State = State.DISCONNECTED

    private var room: Room? = null

    private fun disposeRoomBestEffort(target: Room?) {
        if (target == null) return
        runCatching { target.disconnect() }
        runCatching { target.release() }
    }

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
        if (!permissionGranted()) {
            state = State.FAILED
            return Result.failure(
                SecurityException("Microphone permission is required before starting Sentinel VoIP media")
            )
        }

        state = State.CONNECTING
        var pendingRoom: Room? = null
        try {
            val connectedRoom = roomFactory()
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
            disposeRoomBestEffort(pendingRoom)
            disposeRoomBestEffort(room)
            room = null
            state = State.DISCONNECTED
            throw cancelled
        } catch (failure: Exception) {
            disposeRoomBestEffort(pendingRoom)
            disposeRoomBestEffort(room)
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
            disposeRoomBestEffort(connectedRoom)
            state = State.DISCONNECTED
        }
    }
}
