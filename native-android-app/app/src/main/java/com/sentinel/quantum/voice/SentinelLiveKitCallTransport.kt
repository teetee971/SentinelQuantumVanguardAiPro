package com.sentinel.quantum.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val roomFactory: () -> Room,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val awaitExpiry: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
) : VoiceSessionTransportPort {
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
        val accessToken: String,
        val expiresAtEpochMs: Long? = null
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
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var state: State = State.DISCONNECTED

    private var room: Room? = null
    private var linkedTalk: PushToTalkSession? = null
    private var observerJob: Job? = null
    private var publicationSuspended = false

    private fun disposeRoomBestEffort(target: Room?) {
        if (target == null) return
        runCatching { target.disconnect() }
        runCatching { target.release() }
    }

    fun state(): State = state

    override fun configureVoice(effect: VoiceAddonPolicy.Effect) {
        voiceProcessor.configure(enabled = true, effect = effect)
    }

    fun configureVoice(
        enabled: Boolean,
        effect: VoiceAddonPolicy.Effect
    ) {
        voiceProcessor.configure(enabled, effect)
    }

    suspend fun connect(credentials: Credentials): Result<Unit> {
        val connection = connectInternal(credentials, publishMicrophone = true)
        if (connection.isSuccess) observe(credentials.expiresAtEpochMs)
        return connection
    }

    /**
     * Connects to a Sentinel room without opening the microphone.
     *
     * The returned session owns the only microphone gate and enables media exclusively while
     * the user holds the push-to-talk control.
     */
    override suspend fun connectForPushToTalk(
        credentials: Credentials
    ): Result<PushToTalkSession> {
        val connection = connectInternal(credentials, publishMicrophone = false)
        if (connection.isFailure) return Result.failure(connection.exceptionOrNull() ?: IllegalStateException("VOICE_TRANSPORT_CONNECT_FAILED"))
        val session = PushToTalkSession { enabled -> setMicrophoneEnabled(enabled) }
        mutex.withLock { linkedTalk = session }
        observe(credentials.expiresAtEpochMs)
        return Result.success(session)
    }

    private suspend fun connectInternal(
        credentials: Credentials,
        publishMicrophone: Boolean
    ): Result<Unit> = mutex.withLock {
        if (!credentials.validate()) {
            state = State.FAILED
            return Result.failure(IllegalArgumentException("Invalid secure LiveKit credentials"))
        }
        val expiry = credentials.expiresAtEpochMs
        if (expiry != null && expiry <= nowMs()) {
            state = State.FAILED
            return Result.failure(IllegalStateException("LiveKit token is expired"))
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

            if (publishMicrophone) {
                val microphonePublished =
                    connectedRoom.localParticipant.setMicrophoneEnabled(true)
                if (!microphonePublished) {
                    error("LiveKit microphone publication failed")
                }
                state = State.ACTIVE_MIC
            }
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

    /**
     * Changes the microphone state for an already-connected room.
     *
     * PTT callers use this method through PushToTalkSession. It never creates a room and
     * fails closed when the transport is disconnected or already failed.
     */
    suspend fun setMicrophoneEnabled(enabled: Boolean): Result<Unit> = mutex.withLock {
        val connectedRoom = room
            ?: return Result.failure(
                IllegalStateException("Microphone gate requires a connected LiveKit room")
            )
        if (publicationSuspended && enabled) {
            return Result.failure(LiveKitPublicationSuspended())
        }
        if (state != State.CONNECTED && state != State.ACTIVE_MIC) {
            return Result.failure(
                IllegalStateException("Microphone gate is unavailable in state $state")
            )
        }

        try {
            val changed = connectedRoom.localParticipant.setMicrophoneEnabled(enabled)
            if (!changed) {
                state = State.FAILED
                return Result.failure(
                    IllegalStateException("LiveKit microphone update was rejected")
                )
            }
            state = if (enabled) State.ACTIVE_MIC else State.CONNECTED
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            if (enabled) {
                withContext(NonCancellable) {
                    runCatching { connectedRoom.localParticipant.setMicrophoneEnabled(false) }
                }
                state = State.CONNECTED
            }
            throw cancelled
        } catch (failure: Exception) {
            state = State.FAILED
            Result.failure(failure)
        }
    }

    override suspend fun disconnect() = mutex.withLock {
        observerJob?.cancel()
        observerJob = null
        linkedTalk = null
        publicationSuspended = false
        val connectedRoom = room
        room = null
        try {
            connectedRoom?.localParticipant?.setMicrophoneEnabled(false)
        } finally {
            disposeRoomBestEffort(connectedRoom)
            state = State.DISCONNECTED
        }
        Unit
    }

    private fun observe(expiresAtEpochMs: Long?) {
        val connectedRoom = room ?: return
        observerJob?.cancel()
        observerJob = observerScope.launch {
            if (expiresAtEpochMs != null) {
                launch {
                    val wait = expiresAtEpochMs - nowMs()
                    if (wait > 0L) awaitExpiry(wait)
                    handle(LiveKitSyncSignal.TOKEN_EXPIRED)
                }
            }
            launch {
                connectedRoom.events.collect { event ->
                    when (event) {
                        is RoomEvent.Disconnected -> handle(LiveKitSyncSignal.DISCONNECTED)
                        is RoomEvent.Reconnecting -> handle(LiveKitSyncSignal.RECONNECTING)
                        is RoomEvent.Reconnected -> handle(LiveKitSyncSignal.RECONNECTED)
                        is RoomEvent.TrackUnpublished ->
                            if (isLocalMicrophone(event.participant, event.publication.source)) {
                                handle(LiveKitSyncSignal.LOCAL_MIC_LOST)
                            }
                        is RoomEvent.TrackMuted ->
                            if (isLocalMicrophone(event.participant, event.publication.source)) {
                                handle(LiveKitSyncSignal.LOCAL_MIC_LOST)
                            }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun isLocalMicrophone(participant: io.livekit.android.room.participant.Participant, source: Track.Source): Boolean =
        participant is LocalParticipant && source == Track.Source.MICROPHONE

    private suspend fun handle(signal: LiveKitSyncSignal) {
        val followUp = mutex.withLock {
            val talk = linkedTalk
            when (LiveKitRoomSync.followUp(signal)) {
                LiveKitSyncFollowUp.NONE -> null
                LiveKitSyncFollowUp.PUBLICATION_LOST -> {
                    val current = room
                    if (current == null) {
                        null
                    } else {
                        val mute = state == State.ACTIVE_MIC
                        publicationSuspended = signal != LiveKitSyncSignal.RECONNECTED
                        state = State.CONNECTED
                        SyncAction(
                            room = if (mute) current else null,
                            talk = talk,
                            kind = LiveKitSyncFollowUp.PUBLICATION_LOST
                        )
                    }
                }
                LiveKitSyncFollowUp.ROOM_GONE -> {
                    if (room == null && state == State.DISCONNECTED) {
                        null
                    } else {
                        val current = room
                        room = null
                        publicationSuspended = false
                        state = State.DISCONNECTED
                        SyncAction(current, talk, LiveKitSyncFollowUp.ROOM_GONE)
                    }
                }
            }
        } ?: return
        withContext(NonCancellable) {
            if (followUp.kind == LiveKitSyncFollowUp.PUBLICATION_LOST) {
                followUp.room?.let { target ->
                    runCatching { target.localParticipant.setMicrophoneEnabled(false) }
                }
            } else if (followUp.kind == LiveKitSyncFollowUp.ROOM_GONE) {
                disposeRoomBestEffort(followUp.room)
            }
            when (followUp.kind) {
                LiveKitSyncFollowUp.PUBLICATION_LOST -> followUp.talk?.onRoomPublicationLost()
                LiveKitSyncFollowUp.ROOM_GONE -> followUp.talk?.onRoomGone()
                LiveKitSyncFollowUp.NONE -> Unit
            }
        }
        if (followUp.kind == LiveKitSyncFollowUp.ROOM_GONE) observerJob?.cancel()
    }

    private data class SyncAction(
        val room: Room?,
        val talk: PushToTalkSession?,
        val kind: LiveKitSyncFollowUp
    )
}
