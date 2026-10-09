package com.sentinel.quantum.talkiewalkie

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.sentinel.quantum.voice.LiveKitVoiceAudioProcessor
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Small boundary around the LiveKit room used by PTT.
 *
 * The boundary keeps the transport unit-testable while the production adapter below still consumes
 * real LiveKit reconnect events. Reconnect is deliberately treated as a mute boundary: LiveKit may
 * preserve published tracks across reconnect, but Sentinel PTT never restores transmit implicitly.
 */
interface LiveKitTalkieWalkieRoom {
    suspend fun connect(url: String, token: String)

    suspend fun setMicrophoneEnabled(enabled: Boolean): Boolean

    fun setReconnectedHandler(handler: suspend () -> Unit)

    fun disconnect()

    fun release()
}

class LiveKitTalkieWalkieTransport(
    private val permissionGranted: () -> Boolean,
    private val roomFactory: () -> LiveKitTalkieWalkieRoom
) : TalkieWalkieTransport {
    constructor(
        context: Context,
        voiceProcessor: LiveKitVoiceAudioProcessor
    ) : this(
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
        ): () -> LiveKitTalkieWalkieRoom = {
            RealLiveKitTalkieWalkieRoom(
                LiveKit.create(
                    appContext = appContext,
                    overrides = voiceProcessor.liveKitOverrides()
                )
            )
        }
    }

    private val mutex = Mutex()

    @Volatile
    private var state = TalkieWalkieTransport.ConnectionState.DISCONNECTED

    @Volatile
    private var microphoneEnabled = false

    private var room: LiveKitTalkieWalkieRoom? = null

    override fun isMicrophoneEnabled(): Boolean = microphoneEnabled

    override fun connectionState(): TalkieWalkieTransport.ConnectionState = state

    override suspend fun connect(
        credentials: TalkieWalkieTransport.Credentials
    ): Result<Unit> = mutex.withLock {
        if (!credentials.validate()) {
            microphoneEnabled = false
            state = TalkieWalkieTransport.ConnectionState.FAILED
            return Result.failure(IllegalArgumentException("Invalid secure LiveKit PTT credentials"))
        }
        if (room != null) {
            return Result.failure(IllegalStateException("PTT room already connected"))
        }
        if (!permissionGranted()) {
            microphoneEnabled = false
            state = TalkieWalkieTransport.ConnectionState.FAILED
            return Result.failure(
                SecurityException("Microphone permission is required before starting Sentinel PTT media")
            )
        }

        state = TalkieWalkieTransport.ConnectionState.CONNECTING
        var pendingRoom: LiveKitTalkieWalkieRoom? = null
        try {
            val connectedRoom = roomFactory()
            pendingRoom = connectedRoom
            connectedRoom.setReconnectedHandler {
                forceMuteAfterReconnect(connectedRoom)
            }
            connectedRoom.connect(
                url = credentials.serverUrl,
                token = credentials.accessToken
            )
            val muted = connectedRoom.setMicrophoneEnabled(false)
            if (!muted) {
                error("LiveKit PTT microphone failed to enter muted state")
            }

            microphoneEnabled = false
            room = connectedRoom
            pendingRoom = null
            state = TalkieWalkieTransport.ConnectionState.CONNECTED
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            microphoneEnabled = false
            muteAndDisposeBestEffort(pendingRoom)
            muteAndDisposeBestEffort(room)
            room = null
            state = TalkieWalkieTransport.ConnectionState.DISCONNECTED
            throw cancelled
        } catch (failure: Exception) {
            microphoneEnabled = false
            muteAndDisposeBestEffort(pendingRoom)
            muteAndDisposeBestEffort(room)
            room = null
            state = TalkieWalkieTransport.ConnectionState.FAILED
            Result.failure(failure)
        }
    }

    override suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit> = mutex.withLock {
        val connectedRoom = room
            ?: return Result.failure(IllegalStateException("PTT room is not connected"))
        if (state != TalkieWalkieTransport.ConnectionState.CONNECTED) {
            return Result.failure(IllegalStateException("PTT transport is not ready"))
        }
        if (enabled && !permissionGranted()) {
            microphoneEnabled = false
            runCatching { connectedRoom.setMicrophoneEnabled(false) }
            return Result.failure(SecurityException("Microphone permission was revoked"))
        }

        try {
            val changed = connectedRoom.setMicrophoneEnabled(enabled)
            if (!changed) {
                if (enabled) {
                    microphoneEnabled = false
                    runCatching { connectedRoom.setMicrophoneEnabled(false) }
                }
                return Result.failure(IllegalStateException("LiveKit PTT microphone state change failed"))
            }
            microphoneEnabled = enabled
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            if (enabled) {
                microphoneEnabled = false
                runCatching { connectedRoom.setMicrophoneEnabled(false) }
            }
            throw cancelled
        } catch (failure: Exception) {
            microphoneEnabled = false
            runCatching { connectedRoom.setMicrophoneEnabled(false) }
            Result.failure(failure)
        }
    }

    override suspend fun disconnect() = mutex.withLock {
        val connectedRoom = room
        room = null
        microphoneEnabled = false
        try {
            if (connectedRoom != null) {
                runCatching { connectedRoom.setMicrophoneEnabled(false) }
            }
        } finally {
            disposeRoomBestEffort(connectedRoom)
            state = TalkieWalkieTransport.ConnectionState.DISCONNECTED
        }
    }

    private suspend fun forceMuteAfterReconnect(target: LiveKitTalkieWalkieRoom) = mutex.withLock {
        if (room !== target) return@withLock

        microphoneEnabled = false
        val muted = runCatching { target.setMicrophoneEnabled(false) }.getOrDefault(false)
        state = if (muted) {
            TalkieWalkieTransport.ConnectionState.CONNECTED
        } else {
            TalkieWalkieTransport.ConnectionState.FAILED
        }
    }

    private suspend fun muteAndDisposeBestEffort(target: LiveKitTalkieWalkieRoom?) {
        if (target == null) return
        runCatching { target.setMicrophoneEnabled(false) }
        disposeRoomBestEffort(target)
    }

    private fun disposeRoomBestEffort(target: LiveKitTalkieWalkieRoom?) {
        if (target == null) return
        try {
            runCatching { target.disconnect() }
        } finally {
            runCatching { target.release() }
        }
    }
}

private class RealLiveKitTalkieWalkieRoom(
    private val room: Room
) : LiveKitTalkieWalkieRoom {
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var reconnectedHandler: (suspend () -> Unit)? = null

    init {
        eventScope.launch {
            room.events.collect { event ->
                if (event is RoomEvent.Reconnected) {
                    reconnectedHandler?.invoke()
                }
            }
        }
    }

    override suspend fun connect(url: String, token: String) {
        room.connect(url = url, token = token)
    }

    override suspend fun setMicrophoneEnabled(enabled: Boolean): Boolean =
        room.localParticipant.setMicrophoneEnabled(enabled)

    override fun setReconnectedHandler(handler: suspend () -> Unit) {
        reconnectedHandler = handler
    }

    override fun disconnect() {
        room.disconnect()
    }

    override fun release() {
        eventScope.cancel()
        room.release()
    }
}
