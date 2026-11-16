package com.sentinel.quantum.voice

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fail-closed client-side push-to-talk gate.
 *
 * The microphone is opened only for the duration of an accepted press and is closed on
 * release or session close. The supplied gate must be backed by an already-connected
 * Sentinel transport; this class does not create rooms or mint credentials.
 */
class PushToTalkSession(
    private val microphoneGate: suspend (Boolean) -> Result<Unit>
) {
    enum class State {
        READY,
        TRANSMITTING,
        FAILED,
        CLOSED
    }

    private val mutex = Mutex()

    @Volatile
    private var state: State = State.READY

    fun state(): State = state

    suspend fun press(): Result<Unit> = mutex.withLock {
        if (state != State.READY) {
            return Result.failure(
                IllegalStateException("Push-to-talk press is unavailable in state $state")
            )
        }

        val result = microphoneGate(true)
        if (result.isSuccess) {
            state = State.TRANSMITTING
        } else {
            state = State.FAILED
        }
        result
    }

    suspend fun release(): Result<Unit> = mutex.withLock {
        when (state) {
            State.READY -> Result.success(Unit)
            State.TRANSMITTING -> {
                val result = microphoneGate(false)
                if (result.isSuccess) state = State.READY else state = State.FAILED
                result
            }
            State.FAILED,
            State.CLOSED -> Result.failure(
                IllegalStateException("Push-to-talk release is unavailable in state $state")
            )
        }
    }

    suspend fun close(): Result<Unit> = mutex.withLock {
        if (state == State.CLOSED) return Result.success(Unit)

        val muteResult = if (state == State.TRANSMITTING) {
            microphoneGate(false)
        } else {
            Result.success(Unit)
        }

        state = if (muteResult.isSuccess) State.CLOSED else State.FAILED
        muteResult
    }
}
