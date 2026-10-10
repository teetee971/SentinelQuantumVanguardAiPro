package com.sentinel.quantum.talkiewalkie

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TalkieWalkieSessionSnapshot(
    val state: TalkieWalkieState,
    val floorLease: FloorLease?
)

class TalkieWalkieSessionController(
    private val transport: TalkieWalkieTransport,
    private val floorController: FloorController,
    private val watchdog: PttWatchdog,
    private val sessionId: String,
    private val nowMs: () -> Long,
    private val channelPolicy: () -> ChannelPolicy,
    private val microphonePermissionGranted: () -> Boolean,
    private val audioFocusGranted: () -> Boolean,
    initialState: TalkieWalkieState = TalkieWalkieState.DISCONNECTED,
    private val stateMachine: TalkieWalkieStateMachine = TalkieWalkieStateMachine(),
    private val guard: PushToTalkGuard = PushToTalkGuard()
) {
    private val mutex = Mutex()

    @Volatile
    private var state: TalkieWalkieState = initialState

    @Volatile
    private var floorLease: FloorLease? = null

    suspend fun pressToTalk(): Result<Unit> = mutex.withLock {
        if (state != TalkieWalkieState.LISTENING) {
            return@withLock Result.failure(
                IllegalStateException("Push-to-talk requires LISTENING state")
            )
        }

        state = stateMachine.transition(state, TalkieWalkieEvent.PressToTalk)

        val lease = floorController.requestFloor(sessionId, nowMs()).getOrElse { error ->
            state = TalkieWalkieState.LISTENING
            return@withLock Result.failure(error)
        }
        floorLease = lease

        val decision = guard.canTransmit(
            GuardSnapshot(
                nowMs = nowMs(),
                sessionId = sessionId,
                floorLease = lease,
                channelPolicy = channelPolicy(),
                microphonePermissionGranted = microphonePermissionGranted(),
                transportConnected = transport.connectionState() == TalkieWalkieTransport.ConnectionState.CONNECTED,
                audioFocusGranted = audioFocusGranted(),
                state = state
            )
        )
        if (!decision.allowed) {
            floorController.releaseFloor(lease)
            floorLease = null
            state = TalkieWalkieState.LISTENING
            return@withLock Result.failure(
                IllegalStateException("Push-to-talk denied: ${decision.reason}")
            )
        }

        val unmute = transport.setMicrophoneEnabled(true)
        if (unmute.isFailure) {
            floorController.releaseFloor(lease)
            floorLease = null
            state = TalkieWalkieState.LISTENING
            return@withLock Result.failure(
                unmute.exceptionOrNull() ?: IllegalStateException("Microphone enable failed")
            )
        }

        watchdog.arm()
        state = stateMachine.transition(state, TalkieWalkieEvent.FloorGranted)
        Result.success(Unit)
    }

    suspend fun releaseToTalk() = mutex.withLock {
        if (state != TalkieWalkieState.TRANSMITTING) {
            return@withLock
        }

        transport.setMicrophoneEnabled(false)
        watchdog.disarm()
        floorController.stopRenewal()
        floorLease?.let { floorController.releaseFloor(it) }
        floorLease = null
        state = stateMachine.transition(state, TalkieWalkieEvent.ReleaseToTalk)
    }

    suspend fun onTransportLost() = mutex.withLock {
        if (state == TalkieWalkieState.RECONNECTING) {
            return@withLock
        }

        if (state == TalkieWalkieState.TRANSMITTING) {
            if (transport.isMicrophoneEnabled()) {
                transport.setMicrophoneEnabled(false)
            }
            watchdog.disarm()
            floorController.stopRenewal()
            floorLease?.let { floorController.releaseFloor(it) }
            floorLease = null
            state = stateMachine.transition(state, TalkieWalkieEvent.TransportLost)
        }
    }

    suspend fun onTriggerLost() = mutex.withLock {
        if (state != TalkieWalkieState.TRANSMITTING) {
            return@withLock
        }

        if (transport.isMicrophoneEnabled()) {
            transport.setMicrophoneEnabled(false)
        }
        watchdog.disarm()
        floorController.stopRenewal()
        floorLease?.let { floorController.releaseFloor(it) }
        floorLease = null
        state = TalkieWalkieState.LISTENING
    }

    suspend fun onTransportRestored() = mutex.withLock {
        if (state != TalkieWalkieState.RECONNECTING) {
            return@withLock
        }

        if (transport.isMicrophoneEnabled()) {
            transport.setMicrophoneEnabled(false)
        }
        state = stateMachine.transition(state, TalkieWalkieEvent.Reconnected)
    }

    fun snapshot(): TalkieWalkieSessionSnapshot = TalkieWalkieSessionSnapshot(
        state = state,
        floorLease = floorLease
    )
}
