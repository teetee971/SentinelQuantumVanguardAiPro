package com.sentinel.quantum.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

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
    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()

    @Volatile
    private var state: State = State.READY

    fun state(): State = state

    fun addStateListener(listener: (State) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    /** The room dropped the local microphone. The button must not stay on transmit. */
    suspend fun onRoomPublicationLost() = mutex.withLock {
        if (state == State.TRANSMITTING) setState(State.READY)
    }

    /** The room is gone. Further presses fail closed. */
    suspend fun onRoomGone() = mutex.withLock {
        if (state != State.CLOSED) setState(State.FAILED)
    }

    suspend fun press(): Result<Unit> = mutex.withLock {
        if (state != State.READY) {
            return Result.failure(
                IllegalStateException("Push-to-talk press is unavailable in state $state")
            )
        }

        try {
            val result = microphoneGate(true)
            when {
                result.isSuccess -> setState(State.TRANSMITTING)
                result.exceptionOrNull() is LiveKitPublicationSuspended -> setState(State.READY)
                else -> setState(State.FAILED)
            }
            result
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                val muted = microphoneGate(false)
                setState(if (muted.isSuccess) State.READY else State.FAILED)
            }
            throw cancelled
        }
    }

    suspend fun release(): Result<Unit> = mutex.withLock {
        when (state) {
            State.READY -> Result.success(Unit)
            State.TRANSMITTING -> {
                val result = microphoneGate(false)
                if (result.isSuccess) setState(State.READY) else setState(State.FAILED)
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

        setState(if (muteResult.isSuccess) State.CLOSED else State.FAILED)
        muteResult
    }

    private fun setState(next: State) {
        if (state == next) return
        state = next
        listeners.forEach { listener -> runCatching { listener(next) } }
    }
}
